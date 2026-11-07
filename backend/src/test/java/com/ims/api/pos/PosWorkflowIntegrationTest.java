package com.ims.api.pos;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.auth.Role;
import com.ims.api.auth.RoleRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.inventory.InventoryBalance;
import com.ims.api.inventory.InventoryBalanceRepository;
import com.ims.api.inventory.InventoryMovementRepository;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class PosWorkflowIntegrationTest {
    @Autowired private PosService pos;
    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private PosSaleRepository sales;

    private AppUser cashier;
    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Role cashierRole = roles.findByCode("CASHIER")
                .orElseGet(() -> roles.saveAndFlush(new Role("CASHIER", "Cashier")));
        cashier = new AppUser("cashier-" + suffix + "@example.test", "encoded-test-password", "POS Cashier");
        cashier.addRole(cashierRole);
        users.saveAndFlush(cashier);

        warehouse = warehouses.saveAndFlush(new Warehouse("POS-WH-" + suffix, "POS Warehouse " + suffix));
        product = products.saveAndFlush(new Product("POS-SKU-" + suffix, "POS-BAR-" + suffix,
                "POS Product " + suffix, null, null, "each", TrackingType.QUANTITY,
                new BigDecimal("5.0000"), new BigDecimal("12.5000"), BigDecimal.ZERO));
        InventoryBalance balance = new InventoryBalance(product, warehouse);
        balance.setOnHandQuantity(new BigDecimal("5.000"));
        balances.saveAndFlush(balance);
    }

    @Test
    void cashSaleUsesServerPriceRecordsChangeAndDeductsStockOnceOnReplay() {
        UUID key = UUID.randomUUID();
        PosDtos.CreateSaleRequest request = new PosDtos.CreateSaleRequest(key, warehouse.getId(), null,
                java.util.List.of(new PosDtos.LineRequest(product.getId(), new BigDecimal("2.000"))),
                new BigDecimal("2.0000"), PosPaymentMethod.CASH, new BigDecimal("50.0000"), null);

        PosDtos.SaleResponse sale = pos.createSale(request, cashier.getEmail());
        PosDtos.SaleResponse replay = pos.createSale(request, cashier.getEmail());
        assertEquals(sale.id(), replay.id());
        assertEquals(0, new BigDecimal("23.0000").compareTo(sale.total()));
        assertEquals(0, new BigDecimal("27.0000").compareTo(sale.changeGiven()));
        assertEquals(0, new BigDecimal("12.5000").compareTo(sale.items().getFirst().unitPrice()));
        assertEquals(0, new BigDecimal("3.000").compareTo(stock().getOnHandQuantity()));
        assertEquals(1, movements.findAll().stream()
                .filter(movement -> movement.getMovementType().equals("POS_SALE")
                        && sale.saleNumber().equals(movement.getReferenceId())).count());

        PosDtos.CreateSaleRequest changed = new PosDtos.CreateSaleRequest(key, warehouse.getId(), null,
                java.util.List.of(new PosDtos.LineRequest(product.getId(), new BigDecimal("1.000"))),
                new BigDecimal("2.0000"), PosPaymentMethod.CASH, new BigDecimal("50.0000"), null);
        assertThrows(ApiException.class, () -> pos.createSale(changed, cashier.getEmail()));
    }

    @Test
    void approvedExternalTerminalSaleStoresOnlyReferenceAndInsufficientStockRollsBack() {
        PosDtos.SaleResponse sale = pos.createSale(new PosDtos.CreateSaleRequest(UUID.randomUUID(),
                warehouse.getId(), null, java.util.List.of(new PosDtos.LineRequest(product.getId(),
                new BigDecimal("1.000"))), BigDecimal.ZERO.setScale(4), PosPaymentMethod.CARD_TERMINAL,
                null, "TERM-APPROVAL-8831"), cashier.getEmail());
        assertEquals(PosPaymentMethod.CARD_TERMINAL, sale.paymentMethod());
        assertEquals("TERM-APPROVAL-8831", sale.paymentReference());
        assertNull(sale.amountTendered());
        assertEquals(0, new BigDecimal("4.000").compareTo(stock().getOnHandQuantity()));

        long countBefore = sales.count();
        assertThrows(ApiException.class, () -> pos.createSale(new PosDtos.CreateSaleRequest(UUID.randomUUID(),
                warehouse.getId(), null, java.util.List.of(new PosDtos.LineRequest(product.getId(),
                new BigDecimal("5.000"))), BigDecimal.ZERO.setScale(4), PosPaymentMethod.CASH,
                new BigDecimal("100.0000"), null), cashier.getEmail()));
        assertEquals(countBefore, sales.count());
        assertEquals(0, new BigDecimal("4.000").compareTo(stock().getOnHandQuantity()));
    }

    private InventoryBalance stock() {
        return balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent().stream()
                .filter(balance -> balance.getProduct().getId().equals(product.getId()))
                .findFirst().orElseThrow();
    }
}

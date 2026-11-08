package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class SupplierReturnWorkflowIntegrationTest {
    @Autowired private PurchaseOrderService purchaseOrders;
    @Autowired private SupplierReturnService supplierReturns;
    @Autowired private SupplierRepository suppliers;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private ProductRepository products;
    @Autowired private AppUserRepository users;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private InventoryMovementRepository movements;

    private Supplier supplier;
    private Warehouse warehouse;
    private Product product;
    private AppUser buyer;
    private AppUser approver;
    private AppUser receiver;
    private PurchaseOrderDtos.Response receivedOrder;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        supplier = suppliers.saveAndFlush(new Supplier("SUP-" + suffix, "Supply " + suffix, "A. Contact",
                null, "+63 2 555 0100", null, null, null, "Manila", "NCR", null, "PH", null));
        warehouse = warehouses.saveAndFlush(new Warehouse("WH-" + suffix, "Warehouse " + suffix));
        product = products.saveAndFlush(new Product("SKU-" + suffix, null, "Product " + suffix, null, null,
                "each", TrackingType.QUANTITY, new BigDecimal("10.0000"), new BigDecimal("20.0000"), BigDecimal.ZERO));
        buyer = saveUser("buyer-" + suffix + "@example.test");
        approver = saveUser("approver-" + suffix + "@example.test");
        receiver = saveUser("receiver-" + suffix + "@example.test");

        PurchaseOrderDtos.Response draft = purchaseOrders.create(new PurchaseOrderDtos.CreateRequest(
                supplier.getId(), warehouse.getId(), "PHP", LocalDate.now().plusDays(5), "Return test",
                List.of(new PurchaseOrderDtos.LineRequest(product.getId(), new BigDecimal("10.000"), new BigDecimal("5.2500")))),
                buyer.getEmail());
        purchaseOrders.submit(draft.id());
        purchaseOrders.approve(draft.id(), approver.getEmail());
        PurchaseOrderDtos.Response ordered = purchaseOrders.markOrdered(draft.id());
        purchaseOrders.receive(draft.id(), new PurchaseOrderDtos.ReceiveRequest(UUID.randomUUID(), "DELIVERY-RTN",
                null, List.of(new PurchaseOrderDtos.ReceiveLineRequest(ordered.items().getFirst().id(), new BigDecimal("10.000")))),
                receiver.getEmail());
        receivedOrder = purchaseOrders.get(draft.id());
    }

    @Test
    void approvalDispatchAndCreditNoteUpdateStockAndLedgerExactlyOnce() {
        PurchaseOrderDtos.ItemResponse orderItem = receivedOrder.items().getFirst();
        InventoryBalance balance = balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        balance.setDamagedQuantity(new BigDecimal("2.000"));
        balances.saveAndFlush(balance);

        SupplierReturnDtos.CreateRequest request = createRequest(UUID.randomUUID(), orderItem.id(), "4.000", "2.000");
        SupplierReturnDtos.Response pending = supplierReturns.create(request, buyer.getEmail());
        SupplierReturnDtos.Response replay = supplierReturns.create(request, buyer.getEmail());
        assertEquals(pending.id(), replay.id());
        assertEquals(SupplierReturnStatus.PENDING_APPROVAL, pending.status());
        assertEquals(0, new BigDecimal("21.0000").compareTo(pending.expectedCreditAmount()));
        assertEquals(0, new BigDecimal("6.000").compareTo(
                supplierReturns.returnableItems(receivedOrder.id()).getFirst().returnableQuantity()));

        SupplierReturnDtos.Response approved = supplierReturns.approve(pending.id(), approver.getEmail());
        assertEquals(SupplierReturnStatus.APPROVED, approved.status());
        SupplierReturnDtos.Response dispatched = supplierReturns.dispatch(pending.id(), receiver.getEmail());
        assertEquals(SupplierReturnStatus.DISPATCHED, dispatched.status());
        assertEquals(0, new BigDecimal("6.000").compareTo(
                balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent().getFirst().getOnHandQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent().getFirst().getDamagedQuantity()));
        var inventoryHistory = movements.findRecentForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent();
        assertEquals(1, inventoryHistory.stream().filter(move -> move.getMovementType().equals("SUPPLIER_RETURN")).count());
        assertEquals(0, new BigDecimal("-2.000").compareTo(inventoryHistory.stream()
                .filter(move -> move.getMovementType().equals("SUPPLIER_RETURN")).findFirst().orElseThrow().getDamagedQuantityDelta()));
        assertThrows(ApiException.class, () -> supplierReturns.dispatch(pending.id(), receiver.getEmail()));

        UUID creditKey = UUID.randomUUID();
        SupplierReturnDtos.CreditNoteRequest credit = new SupplierReturnDtos.CreditNoteRequest(creditKey,
                "CN-2026-001", new BigDecimal("19.5000"), "Supplier applied restocking deduction");
        SupplierReturnDtos.Response credited = supplierReturns.recordCreditNote(pending.id(), credit, approver.getEmail());
        SupplierReturnDtos.Response creditReplay = supplierReturns.recordCreditNote(pending.id(), credit, approver.getEmail());
        assertEquals(1, credited.creditNotes().size());
        assertEquals(0, new BigDecimal("19.5000").compareTo(creditReplay.creditedAmount()));
        assertThrows(ApiException.class, () -> supplierReturns.recordCreditNote(pending.id(),
                new SupplierReturnDtos.CreditNoteRequest(creditKey, "CN-CHANGED", new BigDecimal("18.0000"), null),
                approver.getEmail()));
    }

    @Test
    void pendingReturnsReserveReceivedQuantitiesAndRejectionReleasesThem() {
        Long orderItemId = receivedOrder.items().getFirst().id();
        SupplierReturnDtos.Response pending = supplierReturns.create(
                createRequest(UUID.randomUUID(), orderItemId, "6.000", "0"), buyer.getEmail());
        assertThrows(ApiException.class, () -> supplierReturns.create(
                createRequest(UUID.randomUUID(), orderItemId, "5.000", "0"), buyer.getEmail()));

        supplierReturns.reject(pending.id(), approver.getEmail(), new SupplierReturnDtos.RejectRequest("Incorrect shipment"));
        assertEquals(0, new BigDecimal("10.000").compareTo(
                supplierReturns.returnableItems(receivedOrder.id()).getFirst().returnableQuantity()));
        SupplierReturnDtos.Response next = supplierReturns.create(
                createRequest(UUID.randomUUID(), orderItemId, "5.000", "0"), buyer.getEmail());
        assertEquals(SupplierReturnStatus.PENDING_APPROVAL, next.status());
    }

    private SupplierReturnDtos.CreateRequest createRequest(UUID key, Long orderItemId, String quantity, String damaged) {
        return new SupplierReturnDtos.CreateRequest(key, receivedOrder.id(), "Supplier discrepancy",
                null, List.of(new SupplierReturnDtos.LineRequest(orderItemId, new BigDecimal(quantity), new BigDecimal(damaged))));
    }

    private AppUser saveUser(String email) {
        return users.saveAndFlush(new AppUser(email, "encoded-test-password", email));
    }
}

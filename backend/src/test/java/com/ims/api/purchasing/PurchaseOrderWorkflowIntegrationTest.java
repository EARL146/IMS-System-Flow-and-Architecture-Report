package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
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
class PurchaseOrderWorkflowIntegrationTest {
    @Autowired private PurchaseOrderService purchaseOrders;
    @Autowired private SupplierRepository suppliers;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private ProductRepository products;
    @Autowired private AppUserRepository users;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private GoodsReceiptRepository receipts;

    private Supplier supplier;
    private Warehouse warehouse;
    private Product product;
    private AppUser buyer;
    private AppUser approver;
    private AppUser receiver;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        supplier = suppliers.saveAndFlush(new Supplier(
                "SUP-" + suffix, "Supply " + suffix, "A. Contact", null,
                "+63 2 555 0100", null, null, null, "Manila", "NCR",
                null, "PH", null));
        warehouse = warehouses.saveAndFlush(new Warehouse("WH-" + suffix, "Warehouse " + suffix));
        product = products.saveAndFlush(new Product(
                "SKU-" + suffix, null, "Product " + suffix, null, null, "each",
                TrackingType.QUANTITY, new BigDecimal("10.0000"), new BigDecimal("20.0000"),
                BigDecimal.ZERO));
        buyer = saveUser("buyer-" + suffix + "@example.test");
        approver = saveUser("approver-" + suffix + "@example.test");
        receiver = saveUser("receiver-" + suffix + "@example.test");
    }

    @Test
    void approvalPartialReceivingAndIdempotentRetryUpdateTheLedgerOnce() {
        PurchaseOrderDtos.Response draft = purchaseOrders.create(
                new PurchaseOrderDtos.CreateRequest(
                        supplier.getId(), warehouse.getId(), "PHP", LocalDate.now().plusDays(5), "Restock",
                        List.of(new PurchaseOrderDtos.LineRequest(
                                product.getId(), new BigDecimal("10.000"), new BigDecimal("10.2500")))),
                buyer.getEmail());

        assertEquals(PurchaseOrderStatus.DRAFT, draft.status());
        PurchaseOrderDtos.Response submitted = purchaseOrders.submit(draft.id());
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, submitted.status());
        PurchaseOrderDtos.Response approved = purchaseOrders.approve(draft.id(), approver.getEmail());
        assertEquals(PurchaseOrderStatus.APPROVED, approved.status());
        PurchaseOrderDtos.Response ordered = purchaseOrders.markOrdered(draft.id());
        assertEquals(PurchaseOrderStatus.ORDERED, ordered.status());

        Long orderLineId = ordered.items().getFirst().id();
        UUID firstKey = UUID.randomUUID();
        PurchaseOrderDtos.ReceiveRequest firstReceipt = new PurchaseOrderDtos.ReceiveRequest(
                firstKey, "DELIVERY-001", "First box",
                List.of(new PurchaseOrderDtos.ReceiveLineRequest(orderLineId, new BigDecimal("4.000"))));

        PurchaseOrderDtos.ReceiptResponse received = purchaseOrders.receive(draft.id(), firstReceipt, receiver.getEmail());
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, purchaseOrders.get(draft.id()).status());
        PurchaseOrderDtos.ReceiptResponse replay = purchaseOrders.receive(draft.id(), firstReceipt, receiver.getEmail());
        assertEquals(received.id(), replay.id());

        var stock = balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent();
        assertEquals(1, stock.size());
        assertEquals(0, new BigDecimal("4.000").compareTo(stock.getFirst().getOnHandQuantity()));

        PurchaseOrderDtos.ReceiveRequest changedPayload = new PurchaseOrderDtos.ReceiveRequest(
                firstKey, "DELIVERY-001", "First box",
                List.of(new PurchaseOrderDtos.ReceiveLineRequest(orderLineId, new BigDecimal("3.000"))));
        assertThrows(ApiException.class,
                () -> purchaseOrders.receive(draft.id(), changedPayload, receiver.getEmail()));

        UUID secondKey = UUID.randomUUID();
        purchaseOrders.receive(draft.id(), new PurchaseOrderDtos.ReceiveRequest(
                secondKey, "DELIVERY-002", null,
                List.of(new PurchaseOrderDtos.ReceiveLineRequest(orderLineId, new BigDecimal("6.000")))),
                receiver.getEmail());
        assertEquals(PurchaseOrderStatus.RECEIVED, purchaseOrders.get(draft.id()).status());

        var finalStock = balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent();
        assertEquals(0, new BigDecimal("10.000").compareTo(finalStock.getFirst().getOnHandQuantity()));
        assertEquals(2, movements.findRecentForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, receipts.count());
    }

    @Test
    void receiptCannotExceedTheOutstandingPurchaseQuantity() {
        PurchaseOrderDtos.Response draft = purchaseOrders.create(
                new PurchaseOrderDtos.CreateRequest(
                        supplier.getId(), warehouse.getId(), "PHP", null, null,
                        List.of(new PurchaseOrderDtos.LineRequest(
                                product.getId(), new BigDecimal("2.000"), new BigDecimal("3.0000")))),
                buyer.getEmail());
        purchaseOrders.submit(draft.id());
        purchaseOrders.approve(draft.id(), approver.getEmail());
        PurchaseOrderDtos.Response ordered = purchaseOrders.markOrdered(draft.id());

        assertThrows(ApiException.class, () -> purchaseOrders.receive(draft.id(),
                new PurchaseOrderDtos.ReceiveRequest(UUID.randomUUID(), null, null,
                        List.of(new PurchaseOrderDtos.ReceiveLineRequest(
                                ordered.items().getFirst().id(), new BigDecimal("2.001")))),
                receiver.getEmail()));
        assertEquals(PurchaseOrderStatus.ORDERED, purchaseOrders.get(draft.id()).status());
        assertEquals(0, balances.count());
    }

    private AppUser saveUser(String email) {
        return users.saveAndFlush(new AppUser(email, "encoded-test-password", email));
    }
}

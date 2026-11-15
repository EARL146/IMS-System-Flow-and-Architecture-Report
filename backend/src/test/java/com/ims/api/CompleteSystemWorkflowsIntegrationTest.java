package com.ims.api;

import com.ims.api.audit.SecurityAuditService;
import com.ims.api.auth.AdminUserDtos;
import com.ims.api.auth.AdminUserService;
import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.auth.AuthDtos;
import com.ims.api.auth.AuthService;
import com.ims.api.auth.PasswordResetToken;
import com.ims.api.auth.PasswordResetTokenRepository;
import com.ims.api.auth.Role;
import com.ims.api.auth.RoleRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.customer.AdminCustomerOrderService;
import com.ims.api.customer.CartService;
import com.ims.api.customer.CustomerDtos;
import com.ims.api.customer.CustomerOrderService;
import com.ims.api.customer.CustomerOrderStatus;
import com.ims.api.customer.FulfillmentType;
import com.ims.api.customer.OrderManagementDtos;
import com.ims.api.customer.PaymentMethod;
import com.ims.api.inventory.InventoryDtos;
import com.ims.api.inventory.InventoryService;
import com.ims.api.notifications.NotificationService;
import com.ims.api.purchasing.PurchaseOrderDtos;
import com.ims.api.purchasing.PurchaseOrderService;
import com.ims.api.purchasing.Supplier;
import com.ims.api.purchasing.SupplierPayableDtos;
import com.ims.api.purchasing.SupplierPayablePaymentMethod;
import com.ims.api.purchasing.SupplierPayableService;
import com.ims.api.purchasing.SupplierPayableStatus;
import com.ims.api.purchasing.SupplierRepository;
import com.ims.api.purchasing.SupplierReturnDtos;
import com.ims.api.purchasing.SupplierReturnService;
import com.ims.api.reports.ReportDtos;
import com.ims.api.reports.ReportService;
import com.ims.api.support.SupportTicketDtos;
import com.ims.api.support.SupportTicketPriority;
import com.ims.api.support.SupportTicketService;
import com.ims.api.support.SupportTicketStatus;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CompleteSystemWorkflowsIntegrationTest {
    @Autowired private InventoryService inventory;
    @Autowired private CartService cart;
    @Autowired private CustomerOrderService customerOrders;
    @Autowired private AdminCustomerOrderService adminOrders;
    @Autowired private PurchaseOrderService purchaseOrders;
    @Autowired private SupplierReturnService supplierReturns;
    @Autowired private SupplierPayableService supplierPayables;
    @Autowired private AuthService auth;
    @Autowired private AdminUserService adminUsers;
    @Autowired private SupportTicketService supportTickets;
    @Autowired private NotificationService notifications;
    @Autowired private SecurityAuditService auditService;
    @Autowired private ReportService reports;
    @Autowired private PasswordResetTokenRepository resetTokens;
    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private SupplierRepository suppliers;
    @Autowired private PasswordEncoder passwordEncoder;

    private String suffix;
    private String adminEmail;
    private String customerEmail;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product product;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Role adminRole = roles.findByCode("ADMIN").orElseGet(() -> roles.saveAndFlush(new Role("ADMIN", "Admin")));
        Role customerRole = roles.findByCode("CUSTOMER").orElseGet(() -> roles.saveAndFlush(new Role("CUSTOMER", "Customer")));
        roles.findByCode("INVENTORY_MANAGER").orElseGet(() -> roles.saveAndFlush(new Role("INVENTORY_MANAGER", "Inventory Manager")));

        adminEmail = "admin-" + suffix + "@example.test";
        AppUser admin = new AppUser(adminEmail, passwordEncoder.encode("StrongPass!123"), "Admin " + suffix);
        admin.addRole(adminRole);
        users.saveAndFlush(admin);

        customerEmail = "cust-" + suffix + "@example.test";
        AppUser cust = new AppUser(customerEmail, passwordEncoder.encode("CustomerPass!123"), "Customer " + suffix);
        cust.addRole(customerRole);
        users.saveAndFlush(cust);

        sourceWarehouse = warehouses.saveAndFlush(new Warehouse("SWH-" + suffix, "Source Warehouse " + suffix));
        destinationWarehouse = warehouses.saveAndFlush(new Warehouse("DWH-" + suffix, "Dest Warehouse " + suffix));
        product = products.saveAndFlush(new Product("SKU-" + suffix, null, "Item " + suffix,
                "", null, "pcs", TrackingType.QUANTITY, new BigDecimal("50.0000"),
                new BigDecimal("100.0000"), new BigDecimal("5.000")));
        supplier = suppliers.saveAndFlush(new Supplier("SUP-" + suffix, "Supplier " + suffix,
                "Contact", "sup-" + suffix + "@example.test", "+63 900 000 0000", "TIN-123",
                "1 Supplier Way", null, "Manila", "NCR", "1000", "PH", "Net 30"));

        inventory.adjust(new InventoryDtos.AdjustmentRequest(
                product.getId(), sourceWarehouse.getId(), new BigDecimal("20.000"),
                UUID.randomUUID(), "Opening stock"), adminEmail);
    }

    @Test
    void betweenWarehouseTransferAndWarehouseScopeEnforcementWorkAtomically() {
        UUID transferKey = UUID.randomUUID();
        InventoryDtos.TransferRequest req = new InventoryDtos.TransferRequest(
                product.getId(), sourceWarehouse.getId(), destinationWarehouse.getId(),
                new BigDecimal("7.000"), transferKey, "Branch replenishment");

        InventoryDtos.TransferResponse first = inventory.transfer(req, adminEmail);
        assertEquals(0, new BigDecimal("13.000").compareTo(first.outboundMovement().quantityAfter()));
        assertEquals(0, new BigDecimal("7.000").compareTo(first.inboundMovement().quantityAfter()));

        // Idempotent replay returns identical result without double deduction
        InventoryDtos.TransferResponse replay = inventory.transfer(req, adminEmail);
        assertEquals(first.outboundMovement().id(), replay.outboundMovement().id());
        assertEquals(first.inboundMovement().id(), replay.inboundMovement().id());

        // Provision a scoped staff user restricted ONLY to destinationWarehouse
        AdminUserDtos.AdminUserResponse scopedStaff = adminUsers.create(new AdminUserDtos.CreateUserRequest(
                "scoped-" + suffix + "@example.test",
                "ScopedPass!12345",
                "Scoped Manager",
                Set.of("INVENTORY_MANAGER"),
                Set.of(destinationWarehouse.getId())
        ), adminEmail);

        // Scoped user cannot transfer out of sourceWarehouse (403 Forbidden)
        ApiException forbidden = assertThrows(ApiException.class, () -> inventory.transfer(
                new InventoryDtos.TransferRequest(product.getId(), sourceWarehouse.getId(), destinationWarehouse.getId(),
                        new BigDecimal("1.000"), UUID.randomUUID(), "Unauthorized source warehouse"),
                scopedStaff.email()));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
    }

    @Test
    void storePickupCheckoutReadyForPickupTransitionAndNotificationsWorkEndToEnd() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("2.000")));
        CustomerDtos.CheckoutRequest pickupCheckout = new CustomerDtos.CheckoutRequest(
                UUID.randomUUID(), null, sourceWarehouse.getId(),
                PaymentMethod.CASH_ON_DELIVERY, "Pick up at front desk", FulfillmentType.STORE_PICKUP);

        CustomerDtos.OrderResponse placed = customerOrders.checkout(customerEmail, pickupCheckout);
        assertEquals(FulfillmentType.STORE_PICKUP, placed.fulfillmentType());
        assertEquals(0, BigDecimal.ZERO.compareTo(placed.shippingAmount()));
        assertEquals("Store Pickup", placed.addressLabel());

        adminOrders.transition(placed.id(), new OrderManagementDtos.StatusRequest(CustomerOrderStatus.CONFIRMED, "Confirmed"), adminEmail);
        adminOrders.transition(placed.id(), new OrderManagementDtos.StatusRequest(CustomerOrderStatus.PROCESSING, "Packing"), adminEmail);
        CustomerDtos.OrderResponse ready = adminOrders.transition(
                placed.id(), new OrderManagementDtos.StatusRequest(CustomerOrderStatus.READY_FOR_PICKUP, "Ready at counter"), adminEmail);
        assertEquals(CustomerOrderStatus.READY_FOR_PICKUP, ready.status());

        CustomerDtos.OrderResponse pickedUp = adminOrders.transition(
                placed.id(), new OrderManagementDtos.StatusRequest(CustomerOrderStatus.DELIVERED, "Picked up by customer"), adminEmail);
        assertEquals(CustomerOrderStatus.DELIVERED, pickedUp.status());

        var feed = notifications.feed(customerEmail, 20);
        assertTrue(feed.unreadCount() >= 2);
    }

    @Test
    void supplierPayablesReconcileSupplierReturnCreditNotesAndSettleBalance() {
        PurchaseOrderDtos.Response po = purchaseOrders.create(new PurchaseOrderDtos.CreateRequest(
                supplier.getId(), sourceWarehouse.getId(), "PHP", LocalDate.now().plusDays(7), "PO for payables",
                List.of(new PurchaseOrderDtos.LineRequest(product.getId(), new BigDecimal("10.000"), new BigDecimal("50.0000")))
        ), adminEmail);
        purchaseOrders.submit(po.id());
        purchaseOrders.approve(po.id(), adminEmail);
        purchaseOrders.markOrdered(po.id());
        PurchaseOrderDtos.ReceiptResponse receipt = purchaseOrders.receive(po.id(), new PurchaseOrderDtos.ReceiveRequest(
                UUID.randomUUID(), "DR-" + suffix, "Initial receipt",
                List.of(new PurchaseOrderDtos.ReceiveLineRequest(po.items().getFirst().id(), new BigDecimal("10.000")))
        ), adminEmail);
        assertNotNull(receipt.id());

        // Create supplier payable for the 500.00 received PO
        SupplierPayableDtos.Response payable = supplierPayables.createOrReconcile(
                new SupplierPayableDtos.CreateRequest(po.id(), "INV-" + suffix, LocalDate.now().plusDays(30), "Supplier invoice"),
                adminEmail);
        assertEquals(0, new BigDecimal("500.0000").compareTo(payable.grossAmount()));
        assertEquals(0, new BigDecimal("500.0000").compareTo(payable.balanceDue()));
        assertEquals(SupplierPayableStatus.OPEN, payable.status());

        // Return 2 units (value 100.00), approve, dispatch, and record supplier credit note
        SupplierReturnDtos.Response sr = supplierReturns.create(new SupplierReturnDtos.CreateRequest(
                UUID.randomUUID(), po.id(), "Defective batch", null,
                List.of(new SupplierReturnDtos.LineRequest(po.items().getFirst().id(), new BigDecimal("2.000"), BigDecimal.ZERO))
        ), adminEmail);
        supplierReturns.approve(sr.id(), adminEmail);
        supplierReturns.dispatch(sr.id(), adminEmail);
        supplierReturns.recordCreditNote(sr.id(), new SupplierReturnDtos.CreditNoteRequest(
                UUID.randomUUID(), "CN-" + suffix, new BigDecimal("100.0000"), "Credit for 2 returned units"
        ), adminEmail);

        // Reconcile credits on the payable -> net payable drops from 500 to 400
        SupplierPayableDtos.Response reconciled = supplierPayables.reconcileCredits(payable.id(), adminEmail);
        assertEquals(0, new BigDecimal("100.0000").compareTo(reconciled.creditAppliedAmount()));
        assertEquals(0, new BigDecimal("400.0000").compareTo(reconciled.netPayableAmount()));
        assertEquals(0, new BigDecimal("400.0000").compareTo(reconciled.balanceDue()));
        assertEquals(SupplierPayableStatus.PARTIALLY_PAID, reconciled.status());

        // Record remaining 400.00 payment -> status becomes SETTLED
        SupplierPayableDtos.Response settled = supplierPayables.recordPayment(payable.id(),
                new SupplierPayableDtos.PaymentRequest(UUID.randomUUID(), "PAY-" + suffix,
                        SupplierPayablePaymentMethod.BANK_TRANSFER, new BigDecimal("400.0000"), "Full settlement"),
                adminEmail);
        assertEquals(0, BigDecimal.ZERO.compareTo(settled.balanceDue()));
        assertEquals(SupplierPayableStatus.SETTLED, settled.status());
    }

    @Test
    void loginLockoutAdminUnlockAndPasswordResetFlowWorkSecurely() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> auth.login(new AuthDtos.LoginRequest(customerEmail, "WrongPassword!")));
        }
        // 6th attempt even with right password must be rejected with 429 TOO_MANY_REQUESTS
        ApiException lockedEx = assertThrows(ApiException.class, () -> auth.login(new AuthDtos.LoginRequest(customerEmail, "CustomerPass!123")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, lockedEx.getStatus());

        // Request password reset and confirm with a known token hash
        auth.requestPasswordReset(new AuthDtos.PasswordResetRequest(customerEmail));
        AppUser customer = users.findByEmailIgnoreCase(customerEmail).orElseThrow();
        String rawToken = "test-reset-token-" + suffix;
        String tokenHash = sha256(rawToken);
        resetTokens.saveAndFlush(new PasswordResetToken(customer, tokenHash, java.time.Instant.now().plusSeconds(900)));

        auth.confirmPasswordReset(new AuthDtos.PasswordResetConfirmRequest(rawToken, "NewCustomerPass!456"));

        // Confirm password reset unlocked the account and updated the password
        var session = auth.login(new AuthDtos.LoginRequest(customerEmail, "NewCustomerPass!456"));
        assertNotNull(session.response().accessToken());
    }

    @Test
    void supportTicketsAuditTrailAndOperationalReportsWithCsvExportsWork() {
        SupportTicketDtos.Response ticket = supportTickets.createCustomerTicket(customerEmail,
                new SupportTicketDtos.CreateRequest(null, "Question about pickup hours", "STORE_PICKUP",
                        SupportTicketPriority.HIGH, "What time does the pickup counter close?"));
        assertEquals(SupportTicketStatus.OPEN, ticket.status());

        SupportTicketDtos.Response replied = supportTickets.addAdminReply(adminEmail, ticket.id(),
                new SupportTicketDtos.MessageRequest("Our pickup counter is open until 6:00 PM daily."));
        assertEquals(SupportTicketStatus.IN_PROGRESS, replied.status());
        assertEquals(2, replied.messages().size());

        SupportTicketDtos.Response resolved = supportTickets.updateStatus(adminEmail, ticket.id(),
                new SupportTicketDtos.UpdateStatusRequest(SupportTicketStatus.RESOLVED, "Answered pickup schedule"));
        assertEquals(SupportTicketStatus.RESOLVED, resolved.status());

        ReportDtos.OverviewResponse overview = reports.overview();
        assertTrue(overview.inventory().ledgerReconciled());
        assertEquals(0, overview.inventory().discrepancyCount());

        String inventoryCsv = reports.exportCsv("inventory");
        assertTrue(inventoryCsv.contains("SKU-" + suffix));
        String auditCsv = reports.exportCsv("audit");
        assertFalse(auditCsv.isBlank());
        assertTrue(auditService.list(null, 0, 20).totalElements() > 0);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

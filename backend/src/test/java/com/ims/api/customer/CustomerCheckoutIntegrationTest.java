package com.ims.api.customer;

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
import com.ims.api.returns.ReturnDtos;
import com.ims.api.returns.ReturnRefundMethod;
import com.ims.api.returns.ReturnService;
import com.ims.api.returns.ReturnStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class CustomerCheckoutIntegrationTest {
    @Autowired private CustomerAddressService addresses;
    @Autowired private CartService cart;
    @Autowired private CustomerOrderService orders;
    @Autowired private AdminCustomerOrderService adminOrders;
    @Autowired private CustomerOrderRepository customerOrders;
    @Autowired private CustomerOrderStatusHistoryRepository statusHistory;
    @Autowired private CustomerPaymentVerificationRepository paymentVerifications;
    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private ReturnService returns;

    private String customerEmail;
    private String otherCustomerEmail;
    private String staffEmail;
    private Product product;
    private Warehouse warehouse;
    private CustomerDtos.AddressResponse address;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Role customerRole = roles.findByCode("CUSTOMER")
                .orElseGet(() -> roles.saveAndFlush(new Role("CUSTOMER", "Customer")));
        customerEmail = "customer-" + suffix + "@example.test";
        otherCustomerEmail = "other-" + suffix + "@example.test";
        staffEmail = "staff-" + suffix + "@example.test";
        saveCustomer(customerEmail, customerRole);
        saveCustomer(otherCustomerEmail, customerRole);
        users.saveAndFlush(new AppUser(staffEmail, "encoded-test-password", "Staff Tester"));

        warehouse = warehouses.saveAndFlush(new Warehouse("CWH-" + suffix, "Customer Warehouse " + suffix));
        product = products.saveAndFlush(new Product("CSKU-" + suffix, null, "Customer item " + suffix,
                "", null, "each", TrackingType.QUANTITY, new BigDecimal("5.0000"),
                new BigDecimal("12.5000"), BigDecimal.ZERO));
        InventoryBalance stock = new InventoryBalance(product, warehouse);
        stock.setOnHandQuantity(new BigDecimal("5.000"));
        balances.saveAndFlush(stock);
        address = addresses.create(customerEmail, new CustomerDtos.AddressRequest(
                "Home", "Customer Name", "+63 912 000 0000", "12 Example Street", null,
                "Manila", "NCR", "1000", "ph", true));
    }

    @Test
    void checkoutReplayAndCancellationKeepStockAndOrderOwnershipConsistent() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("3.000")));
        UUID key = UUID.randomUUID();
        CustomerDtos.CheckoutRequest checkout = new CustomerDtos.CheckoutRequest(
                key, address.id(), warehouse.getId(), PaymentMethod.CASH_ON_DELIVERY, "Call on arrival");

        CustomerDtos.OrderResponse created = orders.checkout(customerEmail, checkout);
        assertEquals(CustomerOrderStatus.PENDING, created.status());
        assertEquals(PaymentStatus.UNPAID, created.paymentStatus());
        assertEquals(0, new BigDecimal("37.5000").compareTo(created.total()));
        assertEquals(0, new BigDecimal("5.000").compareTo(stock().getOnHandQuantity()));
        assertEquals(0, new BigDecimal("3.000").compareTo(stock().getReservedQuantity()));
        assertEquals(0, cart.get(customerEmail).items().size());

        CustomerDtos.OrderResponse replay = orders.checkout(customerEmail, checkout);
        assertEquals(created.id(), replay.id());
        assertEquals(1, movements.findAll().stream()
                .filter(movement -> movement.getMovementType().equals("SALE_RESERVATION")
                        && created.orderNumber().equals(movement.getReferenceId())).count());
        assertThrows(ApiException.class, () -> orders.get(created.id(), otherCustomerEmail));
        assertThrows(ApiException.class, () -> orders.checkout(customerEmail,
                new CustomerDtos.CheckoutRequest(key, address.id(), warehouse.getId(),
                        PaymentMethod.MANUAL_TRANSFER, "Changed request")));

        CustomerDtos.OrderResponse cancelled = orders.cancel(created.id(), customerEmail);
        assertEquals(CustomerOrderStatus.CANCELLED, cancelled.status());
        assertEquals(PaymentStatus.CANCELLED, cancelled.paymentStatus());
        assertEquals(0, new BigDecimal("0.000").compareTo(stock().getReservedQuantity()));
        assertEquals(0, new BigDecimal("5.000").compareTo(stock().getOnHandQuantity()));
        assertEquals(1, movements.findAll().stream()
                .filter(movement -> movement.getMovementType().equals("SALE_RESERVATION_RELEASE")
                        && created.orderNumber().equals(movement.getReferenceId())).count());

        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("6.000")));
        long ordersBefore = customerOrders.count();
        assertThrows(ApiException.class, () -> orders.checkout(customerEmail,
                new CustomerDtos.CheckoutRequest(UUID.randomUUID(), address.id(), warehouse.getId(),
                        PaymentMethod.CASH_ON_DELIVERY, null)));
        assertEquals(ordersBefore, customerOrders.count());
        assertEquals(1, cart.get(customerEmail).items().size());
    }

    @Test
    void manualTransferRemainsAwaitingVerificationAndNeverBecomesPaidAutomatically() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("1.000")));
        CustomerDtos.OrderResponse created = orders.checkout(customerEmail, new CustomerDtos.CheckoutRequest(
                UUID.randomUUID(), address.id(), warehouse.getId(), PaymentMethod.MANUAL_TRANSFER, null));

        assertEquals(PaymentStatus.AWAITING_VERIFICATION, created.paymentStatus());
        assertNotEquals(PaymentStatus.PAID, created.paymentStatus());
    }

    @Test
    void deliveryConsumesReservedStockOnceAndCashCollectionIsAudited() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("3.000")));
        CustomerDtos.OrderResponse created = orders.checkout(customerEmail, new CustomerDtos.CheckoutRequest(
                UUID.randomUUID(), address.id(), warehouse.getId(), PaymentMethod.CASH_ON_DELIVERY, null));

        transition(created.id(), CustomerOrderStatus.CONFIRMED);
        transition(created.id(), CustomerOrderStatus.PROCESSING);
        transition(created.id(), CustomerOrderStatus.SHIPPED);
        CustomerDtos.OrderResponse delivered = transition(created.id(), CustomerOrderStatus.DELIVERED);
        assertEquals(PaymentStatus.UNPAID, delivered.paymentStatus());
        assertEquals(0, new BigDecimal("2.000").compareTo(stock().getOnHandQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stock().getReservedQuantity()));
        assertEquals(1, movements.findAll().stream()
                .filter(movement -> movement.getMovementType().equals("SALE_FULFILLMENT")
                        && created.orderNumber().equals(movement.getReferenceId())).count());

        adminOrders.transition(created.id(), new OrderManagementDtos.StatusRequest(
                CustomerOrderStatus.DELIVERED, "duplicate callback"), staffEmail);
        assertEquals(1, movements.findAll().stream()
                .filter(movement -> movement.getMovementType().equals("SALE_FULFILLMENT")
                        && created.orderNumber().equals(movement.getReferenceId())).count());

        UUID paymentKey = UUID.randomUUID();
        var paymentRequest = new OrderManagementDtos.PaymentVerificationRequest(
                paymentKey, delivered.total(), "CASH-001", "Cash received at delivery");
        var verification = adminOrders.verifyPayment(created.id(), paymentRequest, staffEmail);
        var replay = adminOrders.verifyPayment(created.id(), paymentRequest, staffEmail);
        assertEquals(verification.id(), replay.id());
        assertEquals(verification.id(), paymentVerifications.findByCustomerOrder_Id(created.id())
                .orElseThrow().getId());
        assertEquals(PaymentStatus.PAID, adminOrders.get(created.id()).paymentStatus());
        assertEquals(5, statusHistory.findAllByCustomerOrder_IdOrderByOccurredAtAscIdAsc(created.id()).size());
    }

    @Test
    void manualTransferMustBeVerifiedBeforeShippingAndUsesFullOrderAmount() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("1.000")));
        CustomerDtos.OrderResponse created = orders.checkout(customerEmail, new CustomerDtos.CheckoutRequest(
                UUID.randomUUID(), address.id(), warehouse.getId(), PaymentMethod.MANUAL_TRANSFER, null));
        transition(created.id(), CustomerOrderStatus.CONFIRMED);
        transition(created.id(), CustomerOrderStatus.PROCESSING);

        assertThrows(ApiException.class, () -> transition(created.id(), CustomerOrderStatus.SHIPPED));
        assertEquals(PaymentStatus.AWAITING_VERIFICATION, adminOrders.get(created.id()).paymentStatus());
        assertThrows(ApiException.class, () -> adminOrders.verifyPayment(created.id(),
                new OrderManagementDtos.PaymentVerificationRequest(UUID.randomUUID(),
                        created.total().subtract(new BigDecimal("1.0000")), "BANK-001", null), staffEmail));

        var request = new OrderManagementDtos.PaymentVerificationRequest(
                UUID.randomUUID(), created.total(), "BANK-001", "Matched against bank statement");
        adminOrders.verifyPayment(created.id(), request, staffEmail);
        assertEquals(PaymentStatus.PAID, adminOrders.get(created.id()).paymentStatus());
        transition(created.id(), CustomerOrderStatus.SHIPPED);
        transition(created.id(), CustomerOrderStatus.DELIVERED);
        assertEquals(0, new BigDecimal("4.000").compareTo(stock().getOnHandQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stock().getReservedQuantity()));
    }

    @Test
    void customerOrderHistoryIsOwnershipScoped() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("1.000")));
        CustomerDtos.OrderResponse created = orders.checkout(customerEmail, new CustomerDtos.CheckoutRequest(
                UUID.randomUUID(), address.id(), warehouse.getId(), PaymentMethod.CASH_ON_DELIVERY, null));

        assertEquals(1, orders.history(created.id(), customerEmail).size());
        assertThrows(ApiException.class, () -> orders.history(created.id(), otherCustomerEmail));
    }

    @Test
    void customerReturnRequiresReviewSeparatesDamagedStockAndRecordsCappedRefundIdempotently() {
        cart.add(customerEmail, new CustomerDtos.AddCartItemRequest(product.getId(), new BigDecimal("2.000")));
        CustomerDtos.OrderResponse created = orders.checkout(customerEmail, new CustomerDtos.CheckoutRequest(
                UUID.randomUUID(), address.id(), warehouse.getId(), PaymentMethod.CASH_ON_DELIVERY, null));
        transition(created.id(), CustomerOrderStatus.CONFIRMED);
        transition(created.id(), CustomerOrderStatus.PROCESSING);
        transition(created.id(), CustomerOrderStatus.SHIPPED);
        CustomerDtos.OrderResponse delivered = transition(created.id(), CustomerOrderStatus.DELIVERED);
        adminOrders.verifyPayment(created.id(), new OrderManagementDtos.PaymentVerificationRequest(
                UUID.randomUUID(), delivered.total(), "CASH-RETURN-TEST", "Collected at delivery"), staffEmail);

        long sourceItemId = delivered.items().getFirst().id();
        UUID requestKey = UUID.randomUUID();
        ReturnDtos.CreateReturnRequest request = new ReturnDtos.CreateReturnRequest(requestKey, "Wrong size", null,
                java.util.List.of(new ReturnDtos.RequestedLine(sourceItemId, new BigDecimal("1.000"))));
        ReturnDtos.ReturnResponse requested = returns.requestCustomerOrderReturn(created.id(), customerEmail, request);
        ReturnDtos.ReturnResponse replay = returns.requestCustomerOrderReturn(created.id(), customerEmail, request);
        assertEquals(requested.id(), replay.id());
        assertEquals(ReturnStatus.REQUESTED, requested.status());
        assertThrows(ApiException.class, () -> returns.getCustomerReturn(requested.id(), otherCustomerEmail));
        assertThrows(ApiException.class, () -> returns.requestCustomerOrderReturn(created.id(), customerEmail,
                new ReturnDtos.CreateReturnRequest(UUID.randomUUID(), "Too many", null,
                        java.util.List.of(new ReturnDtos.RequestedLine(sourceItemId, new BigDecimal("2.000"))))));

        long returnItemId = requested.items().getFirst().id();
        ReturnDtos.ReturnResponse approved = returns.approve(requested.id(), new ReturnDtos.ApproveReturnRequest(
                java.util.List.of(new ReturnDtos.ApprovedLine(returnItemId, new BigDecimal("1.000")))), staffEmail);
        assertEquals(ReturnStatus.APPROVED, approved.status());
        ReturnDtos.ReturnResponse inspected = returns.inspect(requested.id(), new ReturnDtos.InspectReturnRequest(
                java.util.List.of(new ReturnDtos.InspectionLine(returnItemId,
                        new BigDecimal("0.500"), new BigDecimal("0.500"), BigDecimal.ZERO.setScale(3)))), staffEmail);
        assertEquals(ReturnStatus.INSPECTED, inspected.status());
        assertEquals(0, new BigDecimal("12.5000").compareTo(inspected.refundAmount()));
        assertEquals(0, new BigDecimal("4.000").compareTo(stock().getOnHandQuantity()));
        assertEquals(0, new BigDecimal("0.500").compareTo(stock().getDamagedQuantity()));
        assertEquals(0, new BigDecimal("3.500").compareTo(stock().getAvailableQuantity()));

        ReturnDtos.RefundRequest refundRequest = new ReturnDtos.RefundRequest(UUID.randomUUID(),
                ReturnRefundMethod.CASH, null, "Cash returned to customer");
        ReturnDtos.ReturnResponse refunded = returns.refund(requested.id(), refundRequest, staffEmail);
        ReturnDtos.ReturnResponse refundReplay = returns.refund(requested.id(), refundRequest, staffEmail);
        assertEquals(ReturnStatus.REFUNDED, refunded.status());
        assertEquals(refunded.refund().refundNumber(), refundReplay.refund().refundNumber());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, adminOrders.get(created.id()).paymentStatus());
        assertEquals(1, movements.findAll().stream().filter(movement ->
                movement.getMovementType().equals("RETURN_RESTOCK")
                        && refunded.returnNumber().equals(movement.getReferenceId())).count());
        assertEquals(1, movements.findAll().stream().filter(movement ->
                movement.getMovementType().equals("RETURN_DAMAGED")
                        && refunded.returnNumber().equals(movement.getReferenceId())).count());
    }

    private CustomerDtos.OrderResponse transition(long orderId, CustomerOrderStatus status) {
        return adminOrders.transition(orderId, new OrderManagementDtos.StatusRequest(status, null), staffEmail);
    }

    private void saveCustomer(String email, Role role) {
        AppUser customer = new AppUser(email, "encoded-test-password", "Customer Test");
        customer.addRole(role);
        users.saveAndFlush(customer);
    }

    private InventoryBalance stock() {
        return balances.findAllForWarehouse(warehouse.getId(), PageRequest.of(0, 10)).getContent().stream()
                .filter(balance -> balance.getProduct().getId().equals(product.getId()))
                .findFirst().orElseThrow();
    }
}

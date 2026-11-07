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
    @Autowired private CustomerOrderRepository customerOrders;
    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private InventoryMovementRepository movements;

    private String customerEmail;
    private String otherCustomerEmail;
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
        saveCustomer(customerEmail, customerRole);
        saveCustomer(otherCustomerEmail, customerRole);

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

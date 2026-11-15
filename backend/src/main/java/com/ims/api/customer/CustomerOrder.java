package com.ims.api.customer;

import com.ims.api.auth.AppUser;
import com.ims.api.warehouse.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_user_id", nullable = false)
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 32)
    private FulfillmentType fulfillmentType = FulfillmentType.DELIVERY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 40)
    private PaymentStatus paymentStatus;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "shipping_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @Column(name = "address_label", nullable = false, length = 80)
    private String addressLabel;

    @Column(name = "recipient_name", nullable = false, length = 160)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 40)
    private String recipientPhone;

    @Column(name = "delivery_address_line1", nullable = false, length = 180)
    private String addressLine1;

    @Column(name = "delivery_address_line2", length = 180)
    private String addressLine2;

    @Column(name = "delivery_city", nullable = false, length = 100)
    private String city;

    @Column(name = "delivery_region", length = 100)
    private String region;

    @Column(name = "delivery_postal_code", length = 24)
    private String postalCode;

    @Column(name = "delivery_country_code", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(length = 1000)
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<CustomerOrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNumber, AppUser customer, Warehouse warehouse,
                         PaymentMethod paymentMethod, PaymentStatus paymentStatus, String currencyCode,
                         BigDecimal subtotal, BigDecimal taxAmount, BigDecimal shippingAmount,
                         BigDecimal discountAmount, BigDecimal total, CustomerAddress address, String notes) {
        this(orderNumber, customer, warehouse, FulfillmentType.DELIVERY, paymentMethod, paymentStatus,
                currencyCode, subtotal, taxAmount, shippingAmount, discountAmount, total, address, notes);
    }

    public CustomerOrder(String orderNumber, AppUser customer, Warehouse warehouse,
                         FulfillmentType fulfillmentType,
                         PaymentMethod paymentMethod, PaymentStatus paymentStatus, String currencyCode,
                         BigDecimal subtotal, BigDecimal taxAmount, BigDecimal shippingAmount,
                         BigDecimal discountAmount, BigDecimal total, CustomerAddress address, String notes) {
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.warehouse = warehouse;
        this.fulfillmentType = fulfillmentType == null ? FulfillmentType.DELIVERY : fulfillmentType;
        this.status = CustomerOrderStatus.PENDING;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.currencyCode = currencyCode;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.shippingAmount = shippingAmount;
        this.discountAmount = discountAmount;
        this.total = total;
        if (address != null) {
            this.addressLabel = address.getLabel();
            this.recipientName = address.getRecipientName();
            this.recipientPhone = address.getPhone();
            this.addressLine1 = address.getAddressLine1();
            this.addressLine2 = address.getAddressLine2();
            this.city = address.getCity();
            this.region = address.getRegion();
            this.postalCode = address.getPostalCode();
            this.countryCode = address.getCountryCode();
        } else {
            this.addressLabel = "Store Pickup";
            this.recipientName = customer.getFullName();
            this.recipientPhone = "Pickup at Counter";
            this.addressLine1 = warehouse.getName() + " (" + warehouse.getCode() + ")";
            this.addressLine2 = null;
            this.city = "Store Pickup";
            this.region = null;
            this.postalCode = null;
            this.countryCode = "PH";
        }
        this.notes = notes;
    }

    public void addItem(CustomerOrderItem item) {
        item.setCustomerOrder(this);
        items.add(item);
    }

    public void cancel(Instant at) {
        if (status != CustomerOrderStatus.PENDING) {
            throw new IllegalStateException("Only a pending order can be cancelled by the customer");
        }
        if (paymentStatus == PaymentStatus.PAID) {
            throw new IllegalStateException("A paid order requires staff review before it can be cancelled");
        }
        status = CustomerOrderStatus.CANCELLED;
        paymentStatus = PaymentStatus.CANCELLED;
        cancelledAt = at;
    }

    public CustomerOrderStatus transitionTo(CustomerOrderStatus nextStatus) {
        if (status == CustomerOrderStatus.PROCESSING
                && fulfillmentType == FulfillmentType.STORE_PICKUP
                && nextStatus == CustomerOrderStatus.SHIPPED) {
            throw new IllegalStateException("Store pickup orders must transition to READY_FOR_PICKUP instead of SHIPPED");
        }
        boolean allowed = switch (status) {
            case PENDING -> nextStatus == CustomerOrderStatus.CONFIRMED;
            case CONFIRMED -> nextStatus == CustomerOrderStatus.PROCESSING;
            case PROCESSING -> nextStatus == CustomerOrderStatus.SHIPPED
                    || nextStatus == CustomerOrderStatus.READY_FOR_PICKUP;
            case READY_FOR_PICKUP, SHIPPED -> nextStatus == CustomerOrderStatus.DELIVERED;
            case DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Order cannot move from " + status + " to " + nextStatus);
        }
        CustomerOrderStatus previousStatus = status;
        status = nextStatus;
        return previousStatus;
    }

    public void markPaymentVerified() {
        if (status == CustomerOrderStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled order cannot receive a payment verification");
        }
        if (paymentStatus == PaymentStatus.PAID) {
            throw new IllegalStateException("This order is already paid");
        }
        paymentStatus = PaymentStatus.PAID;
    }

    public void startReturnRequest() {
        if (status != CustomerOrderStatus.DELIVERED && status != CustomerOrderStatus.RETURNED
                && status != CustomerOrderStatus.RETURN_REQUESTED) {
            throw new IllegalStateException("Only a delivered order can be returned");
        }
        if (paymentStatus != PaymentStatus.PAID && paymentStatus != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("An unpaid order cannot be returned for a refund");
        }
        status = CustomerOrderStatus.RETURN_REQUESTED;
    }

    public void restoreStatusAfterRejectedReturn(boolean hasOtherPendingReturn, boolean hasCompletedReturn) {
        if (status != CustomerOrderStatus.RETURN_REQUESTED || hasOtherPendingReturn) return;
        status = hasCompletedReturn ? CustomerOrderStatus.RETURNED : CustomerOrderStatus.DELIVERED;
    }

    public void markReturnInspected(boolean hasPendingReturn) {
        if (status == CustomerOrderStatus.RETURN_REQUESTED || status == CustomerOrderStatus.DELIVERED
                || status == CustomerOrderStatus.RETURNED) {
            status = hasPendingReturn ? CustomerOrderStatus.RETURN_REQUESTED : CustomerOrderStatus.RETURNED;
        }
    }

    public void applyRefundStatus(PaymentStatus nextPaymentStatus) {
        if (nextPaymentStatus != PaymentStatus.PARTIALLY_REFUNDED && nextPaymentStatus != PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException("Return settlement requires a refund payment status");
        }
        paymentStatus = nextPaymentStatus;
        status = nextPaymentStatus == PaymentStatus.REFUNDED
                ? CustomerOrderStatus.REFUNDED : CustomerOrderStatus.RETURNED;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public AppUser getCustomer() { return customer; }
    public Warehouse getWarehouse() { return warehouse; }
    public FulfillmentType getFulfillmentType() { return fulfillmentType == null ? FulfillmentType.DELIVERY : fulfillmentType; }
    public CustomerOrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getShippingAmount() { return shippingAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotal() { return total; }
    public String getAddressLabel() { return addressLabel; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public String getNotes() { return notes; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CustomerOrderItem> getItems() { return items; }
}

package com.ims.api.customer;

import com.ims.api.audit.SecurityAuditService;
import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.inventory.InventoryService;
import com.ims.api.notifications.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AdminCustomerOrderService {
    private final CustomerOrderRepository orders;
    private final CustomerOrderStatusHistoryRepository statusHistory;
    private final CustomerPaymentVerificationRepository paymentVerifications;
    private final AppUserRepository users;
    private final InventoryService inventory;
    private final NotificationService notifications;
    private final SecurityAuditService auditService;

    public AdminCustomerOrderService(
            CustomerOrderRepository orders,
            CustomerOrderStatusHistoryRepository statusHistory,
            CustomerPaymentVerificationRepository paymentVerifications,
            AppUserRepository users,
            InventoryService inventory,
            NotificationService notifications,
            SecurityAuditService auditService
    ) {
        this.orders = orders;
        this.statusHistory = statusHistory;
        this.paymentVerifications = paymentVerifications;
        this.users = users;
        this.inventory = inventory;
        this.notifications = notifications;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDtos.OrderResponse> list(CustomerOrderStatus status, int page, int size) {
        var result = status == null
                ? orders.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : orders.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
        return PageResponse.from(result.map(CustomerDtos.OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.OrderResponse get(long orderId) {
        return CustomerDtos.OrderResponse.from(loadDetailed(orderId));
    }

    @Transactional(readOnly = true)
    public java.util.List<CustomerDtos.OrderStatusHistoryResponse> history(long orderId) {
        loadDetailed(orderId);
        return statusHistory.findAllByCustomerOrder_IdOrderByOccurredAtAscIdAsc(orderId).stream()
                .map(CustomerDtos.OrderStatusHistoryResponse::from).toList();
    }

    @Transactional
    public CustomerDtos.OrderResponse transition(
            long orderId,
            OrderManagementDtos.StatusRequest request,
            String actorEmail
    ) {
        AppUser actor = requireUser(actorEmail);
        CustomerOrder order = lockDetailed(orderId);
        if (!actor.canAccessWarehouse(order.getWarehouse().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is not scoped to operate on this order's warehouse");
        }
        if (request.status() == order.getStatus()) {
            return CustomerDtos.OrderResponse.from(order);
        }
        if ((request.status() == CustomerOrderStatus.SHIPPED || request.status() == CustomerOrderStatus.READY_FOR_PICKUP)
                && order.getPaymentMethod() == PaymentMethod.MANUAL_TRANSFER
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.conflict("Verify the manual transfer before shipping or preparing this order for pickup");
        }

        CustomerOrderStatus previousStatus = order.getStatus();
        try {
            order.transitionTo(request.status());
        } catch (IllegalStateException exception) {
            throw ApiException.conflict(exception.getMessage());
        }

        if (request.status() == CustomerOrderStatus.DELIVERED) {
            for (CustomerOrderItem item : order.getItems()) {
                inventory.fulfillCustomerOrderStock(item.getProduct().getId(), order.getWarehouse().getId(),
                        item.getQuantity(), actorEmail, fulfillmentKey(order.getId(), item.getId()),
                        order.getOrderNumber());
            }
        }

        statusHistory.save(new CustomerOrderStatusHistory(order, previousStatus, request.status(), actor,
                clean(request.note())));
        orders.saveAndFlush(order);

        notifications.notifyUser(
                order.getCustomer(),
                "ORDER_UPDATE",
                "Order " + order.getOrderNumber() + " moved to " + request.status().name().replace('_', ' '),
                "Your order " + order.getOrderNumber() + " status is now " + request.status().name().replace('_', ' ') + ".",
                "CUSTOMER_ORDER",
                order.getOrderNumber()
        );
        auditService.record("ORDER_STATUS_CHANGED", actorEmail, "CUSTOMER_ORDER", order.getOrderNumber(),
                "Order " + order.getOrderNumber() + " moved from " + previousStatus + " to " + request.status());

        return CustomerDtos.OrderResponse.from(order);
    }

    @Transactional
    public OrderManagementDtos.PaymentVerificationResponse verifyPayment(
            long orderId,
            OrderManagementDtos.PaymentVerificationRequest request,
            String actorEmail
    ) {
        CustomerOrder order = lockDetailed(orderId);
        String key = request.idempotencyKey().toString();
        var replay = paymentVerifications.findByIdempotencyKeyForUpdate(key);
        if (replay.isPresent()) {
            CustomerPaymentVerification existing = replay.get();
            boolean sameRequest = existing.getCustomerOrder().getId().equals(order.getId())
                    && existing.getAmount().compareTo(request.amount()) == 0
                    && java.util.Objects.equals(existing.getReference(), clean(request.reference()))
                    && java.util.Objects.equals(existing.getNotes(), clean(request.notes()));
            if (!sameRequest) {
                throw ApiException.conflict("Payment verification idempotency key was already used for a different request");
            }
            return OrderManagementDtos.PaymentVerificationResponse.from(existing);
        }

        if (order.getStatus() == CustomerOrderStatus.CANCELLED) {
            throw ApiException.conflict("A cancelled order cannot receive a payment verification");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw ApiException.conflict("This order is already marked paid");
        }
        if (request.amount().compareTo(order.getTotal()) != 0) {
            throw ApiException.conflict("The recorded amount must equal the full order total");
        }
        if (order.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                && order.getStatus() != CustomerOrderStatus.DELIVERED) {
            throw ApiException.conflict("Cash on delivery can be recorded only after delivery");
        }
        if (order.getPaymentMethod() == PaymentMethod.MANUAL_TRANSFER
                && order.getPaymentStatus() != PaymentStatus.AWAITING_VERIFICATION) {
            throw ApiException.conflict("This order is not awaiting manual transfer verification");
        }

        AppUser verifier = requireUser(actorEmail);
        order.markPaymentVerified();
        CustomerPaymentVerification verification = paymentVerifications.saveAndFlush(
                new CustomerPaymentVerification(order, verifier, order.getPaymentMethod(), order.getCurrencyCode(),
                        request.amount(), clean(request.reference()), clean(request.notes()), key));
        orders.saveAndFlush(order);

        notifications.notifyUser(
                order.getCustomer(),
                "PAYMENT_VERIFIED",
                "Payment verified for " + order.getOrderNumber(),
                "Your payment of " + order.getCurrencyCode() + " " + request.amount() + " for order " + order.getOrderNumber() + " has been verified.",
                "CUSTOMER_ORDER",
                order.getOrderNumber()
        );
        auditService.record("ORDER_PAYMENT_VERIFIED", actorEmail, "CUSTOMER_ORDER", order.getOrderNumber(),
                "Payment verified for order " + order.getOrderNumber() + " (" + order.getCurrencyCode() + " " + request.amount() + ")");

        return OrderManagementDtos.PaymentVerificationResponse.from(verification);
    }

    private CustomerOrder loadDetailed(long orderId) {
        return orders.findDetailedById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private CustomerOrder lockDetailed(long orderId) {
        return orders.findDetailedForUpdateById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private String fulfillmentKey(long orderId, long itemId) {
        return UUID.nameUUIDFromBytes(("ims:sales:fulfill:" + orderId + ":" + itemId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.ims.api.customer;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/admin/customer-orders")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_STAFF','CUSTOMER_SUPPORT','ACCOUNTANT','CASHIER')")
public class AdminCustomerOrderController {
    private final AdminCustomerOrderService orders;

    public AdminCustomerOrderController(AdminCustomerOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public PageResponse<CustomerDtos.OrderResponse> list(
            @RequestParam(required = false) CustomerOrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.list(status, page, size);
    }

    @GetMapping("/{id}")
    public CustomerDtos.OrderResponse get(@PathVariable @Min(1) long id) {
        return orders.get(id);
    }

    @GetMapping("/{id}/history")
    public List<CustomerDtos.OrderStatusHistoryResponse> history(@PathVariable @Min(1) long id) {
        return orders.history(id);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_STAFF')")
    public CustomerDtos.OrderResponse transition(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody OrderManagementDtos.StatusRequest request,
            Authentication authentication) {
        return orders.transition(id, request, authentication.getName());
    }

    @PostMapping("/{id}/payments/verify")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT','CASHIER')")
    public OrderManagementDtos.PaymentVerificationResponse verifyPayment(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody OrderManagementDtos.PaymentVerificationRequest request,
            Authentication authentication) {
        return orders.verifyPayment(id, request, authentication.getName());
    }
}

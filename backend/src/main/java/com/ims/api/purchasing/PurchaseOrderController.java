package com.ims.api.purchasing;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderService orders;

    public PurchaseOrderController(PurchaseOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','WAREHOUSE_STAFF','INVENTORY_MANAGER')")
    public PageResponse<PurchaseOrderDtos.Response> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return orders.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','WAREHOUSE_STAFF','INVENTORY_MANAGER')")
    public PurchaseOrderDtos.Response get(@PathVariable @Min(1) long id) {
        return orders.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
    public PurchaseOrderDtos.Response create(
            @Valid @RequestBody PurchaseOrderDtos.CreateRequest request,
            Authentication authentication) {
        return orders.create(request, authentication.getName());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
    public PurchaseOrderDtos.Response submit(@PathVariable @Min(1) long id) {
        return orders.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PurchaseOrderDtos.Response approve(@PathVariable @Min(1) long id, Authentication authentication) {
        return orders.approve(id, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PurchaseOrderDtos.Response reject(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody PurchaseOrderDtos.RejectRequest request,
            Authentication authentication) {
        return orders.reject(id, authentication.getName(), request);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
    public PurchaseOrderDtos.Response send(@PathVariable @Min(1) long id) {
        return orders.markOrdered(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PurchaseOrderDtos.Response cancel(@PathVariable @Min(1) long id,
                                              @Valid @RequestBody PurchaseOrderDtos.CancelRequest request,
                                              Authentication authentication) {
        return orders.cancel(id, authentication.getName(), request);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_STAFF','INVENTORY_MANAGER')")
    public PurchaseOrderDtos.ReceiptResponse receive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody PurchaseOrderDtos.ReceiveRequest request,
            Authentication authentication) {
        return orders.receive(id, request, authentication.getName());
    }
}

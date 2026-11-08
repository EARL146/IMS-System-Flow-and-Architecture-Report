package com.ims.api.returns;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnController {
    private final ReturnService returns;

    public AdminReturnController(ReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CUSTOMER_SUPPORT','WAREHOUSE_STAFF','INVENTORY_MANAGER','CASHIER','ACCOUNTANT')")
    public PageResponse<ReturnDtos.ReturnResponse> list(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return returns.listReturns(status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CUSTOMER_SUPPORT','WAREHOUSE_STAFF','INVENTORY_MANAGER','CASHIER','ACCOUNTANT')")
    public ReturnDtos.ReturnResponse get(@PathVariable @Min(1) long id) {
        return returns.getReturn(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CUSTOMER_SUPPORT')")
    public ReturnDtos.ReturnResponse approve(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ReturnDtos.ApproveReturnRequest request,
            Authentication authentication) {
        return returns.approve(id, request, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CUSTOMER_SUPPORT')")
    public ReturnDtos.ReturnResponse reject(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ReturnDtos.RejectReturnRequest request,
            Authentication authentication) {
        return returns.reject(id, request, authentication.getName());
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_STAFF','INVENTORY_MANAGER')")
    public ReturnDtos.ReturnResponse inspect(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ReturnDtos.InspectReturnRequest request,
            Authentication authentication) {
        return returns.inspect(id, request, authentication.getName());
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT','CASHIER')")
    public ReturnDtos.ReturnResponse refund(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ReturnDtos.RefundRequest request,
            Authentication authentication) {
        return returns.refund(id, request, authentication.getName());
    }
}

package com.ims.api.purchasing;

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

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/admin/supplier-returns")
public class SupplierReturnController {
    private final SupplierReturnService supplierReturns;

    public SupplierReturnController(SupplierReturnService supplierReturns) {
        this.supplierReturns = supplierReturns;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','WAREHOUSE_STAFF','INVENTORY_MANAGER','ACCOUNTANT')")
    public PageResponse<SupplierReturnDtos.Response> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return supplierReturns.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','WAREHOUSE_STAFF','INVENTORY_MANAGER','ACCOUNTANT')")
    public SupplierReturnDtos.Response get(@PathVariable @Min(1) long id) {
        return supplierReturns.get(id);
    }

    @GetMapping("/returnable-items")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','WAREHOUSE_STAFF','INVENTORY_MANAGER')")
    public List<SupplierReturnDtos.ReturnableItemResponse> returnableItems(
            @RequestParam @Min(1) long purchaseOrderId) {
        return supplierReturns.returnableItems(purchaseOrderId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
    public SupplierReturnDtos.Response create(@Valid @RequestBody SupplierReturnDtos.CreateRequest request,
                                               Authentication authentication) {
        return supplierReturns.create(request, authentication.getName());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SupplierReturnDtos.Response approve(@PathVariable @Min(1) long id, Authentication authentication) {
        return supplierReturns.approve(id, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SupplierReturnDtos.Response reject(@PathVariable @Min(1) long id,
                                               @Valid @RequestBody SupplierReturnDtos.RejectRequest request,
                                               Authentication authentication) {
        return supplierReturns.reject(id, authentication.getName(), request);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_STAFF','INVENTORY_MANAGER')")
    public SupplierReturnDtos.Response dispatch(@PathVariable @Min(1) long id, Authentication authentication) {
        return supplierReturns.dispatch(id, authentication.getName());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
    public SupplierReturnDtos.Response cancel(@PathVariable @Min(1) long id, Authentication authentication) {
        return supplierReturns.cancel(id, authentication.getName());
    }

    @PostMapping("/{id}/credit-notes")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT')")
    public SupplierReturnDtos.Response recordCreditNote(@PathVariable @Min(1) long id,
                                                         @Valid @RequestBody SupplierReturnDtos.CreditNoteRequest request,
                                                         Authentication authentication) {
        return supplierReturns.recordCreditNote(id, request, authentication.getName());
    }
}

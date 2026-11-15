package com.ims.api.purchasing;

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

@Validated
@RestController
@RequestMapping("/api/v1/admin/supplier-payables")
public class SupplierPayableController {
    private final SupplierPayableService payables;

    public SupplierPayableController(SupplierPayableService payables) {
        this.payables = payables;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','ACCOUNTANT')")
    public PageResponse<SupplierPayableDtos.Response> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return payables.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','ACCOUNTANT')")
    public SupplierPayableDtos.Response get(@PathVariable long id) {
        return payables.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','ACCOUNTANT')")
    public SupplierPayableDtos.Response createOrReconcile(@Valid @RequestBody SupplierPayableDtos.CreateRequest request,
                                                          Authentication authentication) {
        return payables.createOrReconcile(request, authentication.getName());
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER','ACCOUNTANT')")
    public SupplierPayableDtos.Response reconcile(@PathVariable long id, Authentication authentication) {
        return payables.reconcileCredits(id, authentication.getName());
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT')")
    public SupplierPayableDtos.Response recordPayment(@PathVariable long id,
                                                      @Valid @RequestBody SupplierPayableDtos.PaymentRequest request,
                                                      Authentication authentication) {
        return payables.recordPayment(id, request, authentication.getName());
    }
}

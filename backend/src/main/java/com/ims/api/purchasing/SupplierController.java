package com.ims.api.purchasing;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/admin/suppliers")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
public class SupplierController {
    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    public PageResponse<SupplierDtos.SupplierResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return suppliers.list(q, page, size);
    }

    @GetMapping("/{id}")
    public SupplierDtos.SupplierResponse get(@PathVariable @Min(1) long id) {
        return suppliers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDtos.SupplierResponse create(@Valid @RequestBody SupplierDtos.SupplierRequest request) {
        return suppliers.create(request);
    }

    @PutMapping("/{id}")
    public SupplierDtos.SupplierResponse update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody SupplierDtos.SupplierRequest request) {
        return suppliers.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable @Min(1) long id) {
        suppliers.archive(id);
    }
}

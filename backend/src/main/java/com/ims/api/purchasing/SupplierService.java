package com.ims.api.purchasing;

import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SupplierService {
    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierDtos.SupplierResponse> list(String query, int page, int size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(suppliers.search(normalizedQuery, PageRequest.of(page, size))
                .map(SupplierDtos.SupplierResponse::from));
    }

    @Transactional(readOnly = true)
    public SupplierDtos.SupplierResponse get(long id) {
        return SupplierDtos.SupplierResponse.from(suppliers.findById(id)
                .orElseThrow(() -> ApiException.notFound("Supplier not found")));
    }

    @Transactional
    public SupplierDtos.SupplierResponse create(SupplierDtos.SupplierRequest request) {
        String code = normalizeCode(request.code());
        if (suppliers.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Supplier code is already in use");
        }
        Supplier supplier = fromRequest(request, code);
        return SupplierDtos.SupplierResponse.from(suppliers.saveAndFlush(supplier));
    }

    @Transactional
    public SupplierDtos.SupplierResponse update(long id, SupplierDtos.SupplierRequest request) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> ApiException.notFound("Supplier not found"));
        if (!supplier.isActive()) throw ApiException.conflict("Archived suppliers cannot be edited");
        String code = normalizeCode(request.code());
        if (suppliers.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw ApiException.conflict("Supplier code is already in use");
        }
        Supplier updated = fromRequest(request, code);
        supplier.update(code, updated.getName(), updated.getContactName(), updated.getEmail(),
                updated.getPhone(), updated.getTaxIdentifier(), updated.getAddressLine1(),
                updated.getAddressLine2(), updated.getCity(), updated.getRegion(),
                updated.getPostalCode(), updated.getCountryCode(), updated.getNotes());
        return SupplierDtos.SupplierResponse.from(suppliers.saveAndFlush(supplier));
    }

    @Transactional
    public void archive(long id) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> ApiException.notFound("Supplier not found"));
        supplier.archive();
    }

    private Supplier fromRequest(SupplierDtos.SupplierRequest request, String code) {
        return new Supplier(code, request.name().trim(), clean(request.contactName()),
                normalizeEmail(request.email()), clean(request.phone()), clean(request.taxIdentifier()),
                clean(request.addressLine1()), clean(request.addressLine2()), clean(request.city()),
                clean(request.region()), clean(request.postalCode()), normalizeCountry(request.countryCode()),
                clean(request.notes()));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCountry(String country) {
        return country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

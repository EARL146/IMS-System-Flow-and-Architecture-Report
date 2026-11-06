package com.ims.api.purchasing;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SupplierDtos {
    private SupplierDtos() {
    }

    public record SupplierRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 140) String contactName,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 80) String taxIdentifier,
            @Size(max = 180) String addressLine1,
            @Size(max = 180) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 24) String postalCode,
            @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Size(max = 5000) String notes
    ) {
    }

    public record SupplierResponse(
            Long id, String code, String name, String contactName, String email, String phone,
            String taxIdentifier, String addressLine1, String addressLine2, String city,
            String region, String postalCode, String countryCode, String notes,
            boolean active, Instant createdAt, Instant updatedAt
    ) {
        static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(supplier.getId(), supplier.getCode(), supplier.getName(),
                    supplier.getContactName(), supplier.getEmail(), supplier.getPhone(),
                    supplier.getTaxIdentifier(), supplier.getAddressLine1(), supplier.getAddressLine2(),
                    supplier.getCity(), supplier.getRegion(), supplier.getPostalCode(),
                    supplier.getCountryCode(), supplier.getNotes(), supplier.isActive(),
                    supplier.getCreatedAt(), supplier.getUpdatedAt());
        }
    }
}

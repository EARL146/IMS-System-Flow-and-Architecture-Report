package com.ims.api.purchasing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 48)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "contact_name", length = 140)
    private String contactName;

    @Column(length = 254)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "tax_identifier", length = 80)
    private String taxIdentifier;

    @Column(name = "address_line1", length = 180)
    private String addressLine1;

    @Column(name = "address_line2", length = 180)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String region;

    @Column(name = "postal_code", length = 24)
    private String postalCode;

    @Column(name = "country_code", length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Supplier() {
    }

    public Supplier(String code, String name, String contactName, String email, String phone,
                    String taxIdentifier, String addressLine1, String addressLine2, String city,
                    String region, String postalCode, String countryCode, String notes) {
        this.code = code;
        this.name = name;
        updateContact(contactName, email, phone, taxIdentifier, addressLine1, addressLine2,
                city, region, postalCode, countryCode, notes);
    }

    public void update(String code, String name, String contactName, String email, String phone,
                       String taxIdentifier, String addressLine1, String addressLine2, String city,
                       String region, String postalCode, String countryCode, String notes) {
        this.code = code;
        this.name = name;
        updateContact(contactName, email, phone, taxIdentifier, addressLine1, addressLine2,
                city, region, postalCode, countryCode, notes);
    }

    private void updateContact(String contactName, String email, String phone, String taxIdentifier,
                               String addressLine1, String addressLine2, String city, String region,
                               String postalCode, String countryCode, String notes) {
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.taxIdentifier = taxIdentifier;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.notes = notes;
    }

    public void archive() { this.active = false; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getTaxIdentifier() { return taxIdentifier; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

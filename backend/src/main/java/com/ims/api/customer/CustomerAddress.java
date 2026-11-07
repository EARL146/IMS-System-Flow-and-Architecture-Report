package com.ims.api.customer;

import com.ims.api.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 160)
    private String recipientName;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "address_line1", nullable = false, length = 180)
    private String addressLine1;

    @Column(name = "address_line2", length = 180)
    private String addressLine2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String region;

    @Column(name = "postal_code", length = 24)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected CustomerAddress() {
    }

    public CustomerAddress(AppUser user, String label, String recipientName, String phone,
                           String addressLine1, String addressLine2, String city, String region,
                           String postalCode, String countryCode, boolean defaultAddress) {
        this.user = user;
        update(label, recipientName, phone, addressLine1, addressLine2, city, region,
                postalCode, countryCode);
        this.defaultAddress = defaultAddress;
    }

    public void update(String label, String recipientName, String phone, String addressLine1,
                       String addressLine2, String city, String region, String postalCode,
                       String countryCode) {
        this.label = label;
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }

    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public Instant getCreatedAt() { return createdAt; }
}

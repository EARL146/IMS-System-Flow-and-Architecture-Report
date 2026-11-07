package com.ims.api.customer;

import com.ims.api.auth.AppUser;
import com.ims.api.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shopping_cart_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cart_user_product", columnNames = {"user_id", "product_id"})
})
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected CartItem() {
    }

    public CartItem(AppUser user, Product product, BigDecimal quantity) {
        this.user = user;
        this.product = product;
        this.quantity = quantity;
    }

    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Product getProduct() { return product; }
    public BigDecimal getQuantity() { return quantity; }
}

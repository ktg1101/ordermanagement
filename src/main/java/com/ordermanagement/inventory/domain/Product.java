package com.ordermanagement.inventory.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.ordermanagement.shared.common.persistence.AuditableEntity;

@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Column(name = "sku", nullable = false, length = 64, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public static Product create(String sku, String name, String description, BigDecimal price) {
        Product product = new Product();
        product.sku = sku;
        product.name = name;
        product.description = description;
        product.price = price;
        product.active = true;
        return product;
    }

    public void update(String newName, String newDescription, BigDecimal newPrice, boolean newActive) {
        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
        this.active = newActive;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}

package com.ims.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.checkout")
public record CheckoutProperties(
        BigDecimal taxRate,
        BigDecimal shippingAmount,
        String currencyCode
) {
    public CheckoutProperties {
        taxRate = taxRate == null ? BigDecimal.ZERO : taxRate;
        shippingAmount = shippingAmount == null ? BigDecimal.ZERO : shippingAmount;
        currencyCode = currencyCode == null || currencyCode.isBlank() ? "PHP" : currencyCode;
    }
}

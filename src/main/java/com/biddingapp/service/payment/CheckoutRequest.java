package com.biddingapp.service.payment;

import java.math.BigDecimal;
import java.time.Instant;

public class CheckoutRequest {

    private final Long auctionId;
    private final Long payerId;
    private final String customerEmail;
    private final BigDecimal amount;
    private final String currency;
    private final String description;
    private final String successUrl;
    private final String cancelUrl;
    private final Instant paymentDueAt;

    public CheckoutRequest(Long auctionId,
                           Long payerId,
                           String customerEmail,
                           BigDecimal amount,
                           String currency,
                           String description,
                           String successUrl,
                           String cancelUrl,
                           Instant paymentDueAt) {
        this.auctionId = auctionId;
        this.payerId = payerId;
        this.customerEmail = customerEmail;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.paymentDueAt = paymentDueAt;
    }

    public Long getAuctionId() {
        return auctionId;
    }

    public Long getPayerId() {
        return payerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public Instant getPaymentDueAt() {
        return paymentDueAt;
    }
}

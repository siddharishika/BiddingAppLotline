package com.biddingapp.service.payment;

import java.math.BigDecimal;

public class ChargeRequest {

    private final String apiKey;
    private final String cardNumber;
    private final String expiry;
    private final String cvc;
    private final String cardholderName;
    private final BigDecimal amount;
    private final String currency;
    private final String description;

    public ChargeRequest(String apiKey, String cardNumber, String expiry, String cvc,
                         String cardholderName, BigDecimal amount, String currency, String description) {
        this.apiKey = apiKey;
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.cvc = cvc;
        this.cardholderName = cardholderName;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getExpiry() {
        return expiry;
    }

    public String getCvc() {
        return cvc;
    }

    public String getCardholderName() {
        return cardholderName;
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
}

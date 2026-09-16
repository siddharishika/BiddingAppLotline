package com.biddingapp.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stripe-style charge API used in this project. Cards never leave the server
 * beyond this gateway call; only a transaction id and last four digits are stored.
 *
 * Demo cards:
 * 4242 4242 4242 4242 — authorized
 * 4000 0000 0000 0002 — declined
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final String expectedApiKey;

    public SimulatedPaymentGateway(@Value("${lotline.payments.api-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.getApiKey() == null || !request.getApiKey().equals(expectedApiKey)) {
            return ChargeResult.declined("Invalid payment gateway API key");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            return ChargeResult.declined("Charge amount must be positive");
        }

        String digits = request.getCardNumber() == null ? "" : request.getCardNumber().replaceAll("\\s", "");
        if (digits.startsWith("4000000000000002")) {
            return ChargeResult.declined("Card declined by issuing bank");
        }
        if (!digits.startsWith("4242")) {
            return ChargeResult.declined("Unrecognized test card. Use 4242… for success or 4000…0002 for decline.");
        }

        String transactionId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        return ChargeResult.success(transactionId);
    }
}

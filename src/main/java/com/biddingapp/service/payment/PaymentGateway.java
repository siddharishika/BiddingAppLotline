package com.biddingapp.service.payment;

public interface PaymentGateway {

    String providerId();

    boolean usesHostedCheckout();

    CheckoutSessionResult createCheckoutSession(CheckoutRequest request);

    default CheckoutFulfillment retrieveCheckout(String sessionId) {
        return CheckoutFulfillment.ignored();
    }

    default CheckoutFulfillment parseWebhook(String payload, String signatureHeader) {
        return CheckoutFulfillment.ignored();
    }

    default void expireCheckout(String sessionId) {
        // No hosted session to expire.
    }
}

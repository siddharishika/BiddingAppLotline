package com.biddingapp.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory hosted checkout used by unit tests. No cards, no Stripe network.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final Map<String, CheckoutRequest> sessions = new ConcurrentHashMap<>();
    private final Map<String, CheckoutFulfillment.Outcome> outcomes = new ConcurrentHashMap<>();

    @Override
    public String providerId() {
        return "simulated";
    }

    @Override
    public boolean usesHostedCheckout() {
        return true;
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CheckoutRequest request) {
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new PaymentGatewayException("Charge amount must be positive");
        }
        String sessionId = "cs_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        sessions.put(sessionId, request);
        outcomes.put(sessionId, CheckoutFulfillment.Outcome.IGNORED);
        log.warn("Simulated checkout {} for auction {}; Stripe was not contacted", sessionId, request.getAuctionId());
        return new CheckoutSessionResult(sessionId, "https://checkout.local/pay/" + sessionId);
    }

    @Override
    public CheckoutFulfillment retrieveCheckout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return CheckoutFulfillment.ignored();
        }
        CheckoutRequest request = sessions.get(sessionId);
        if (request == null) {
            return CheckoutFulfillment.ignored();
        }
        CheckoutFulfillment.Outcome outcome = outcomes.getOrDefault(sessionId, CheckoutFulfillment.Outcome.IGNORED);
        if (outcome == CheckoutFulfillment.Outcome.EXPIRED) {
            return CheckoutFulfillment.expired(request.getAuctionId(), request.getPayerId(), sessionId);
        }
        if (outcome == CheckoutFulfillment.Outcome.SUCCEEDED) {
            return CheckoutFulfillment.succeeded(
                    request.getAuctionId(),
                    request.getPayerId(),
                    sessionId,
                    "pi_sim_" + sessionId.substring(Math.min(7, sessionId.length())),
                    "4242"
            );
        }
        if (outcome == CheckoutFulfillment.Outcome.FAILED) {
            return CheckoutFulfillment.failed(request.getAuctionId(), request.getPayerId(), sessionId, "Checkout failed");
        }
        return CheckoutFulfillment.ignored();
    }

    @Override
    public void expireCheckout(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !sessions.containsKey(sessionId)) {
            return;
        }
        outcomes.put(sessionId, CheckoutFulfillment.Outcome.EXPIRED);
    }
}

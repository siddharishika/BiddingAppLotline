package com.biddingapp.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

public class StripeCheckoutGateway implements PaymentGateway {

    static final String META_AUCTION_ID = "auctionId";
    static final String META_PAYER_ID = "payerId";

    private final String webhookSecret;
    private final RequestOptions requestOptions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripeCheckoutGateway(String secretKey, String webhookSecret) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("stripe.secret-key is required when lotline.payments.provider=stripe");
        }
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.requestOptions = RequestOptions.builder().setApiKey(secretKey.trim()).build();
    }

    @Override
    public String providerId() {
        return "stripe";
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

        long unitAmount;
        try {
            unitAmount = request.getAmount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new PaymentGatewayException("Amount must have at most two decimal places");
        }

        Instant now = Instant.now();
        Instant minExpiry = now.plus(Duration.ofMinutes(31));
        Instant maxExpiry = now.plus(Duration.ofHours(24));
        Instant expiry = request.getPaymentDueAt() != null && request.getPaymentDueAt().isBefore(maxExpiry)
                ? request.getPaymentDueAt()
                : maxExpiry;
        if (expiry.isBefore(minExpiry)) {
            throw new PaymentGatewayException(
                    "Less than 30 minutes remain in the payment window, so Stripe Checkout cannot be started");
        }

        String name = request.getDescription() == null ? "Lotline lot" : request.getDescription().trim();
        if (name.length() > 240) {
            name = name.substring(0, 240);
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.getSuccessUrl())
                .setCancelUrl(request.getCancelUrl())
                .setExpiresAt(expiry.getEpochSecond())
                .setClientReferenceId(String.valueOf(request.getAuctionId()))
                .putMetadata(META_AUCTION_ID, String.valueOf(request.getAuctionId()))
                .putMetadata(META_PAYER_ID, String.valueOf(request.getPayerId()))
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata(META_AUCTION_ID, String.valueOf(request.getAuctionId()))
                                .putMetadata(META_PAYER_ID, String.valueOf(request.getPayerId()))
                                .build()
                )
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(request.getCurrency().toLowerCase())
                                                .setUnitAmount(unitAmount)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(name)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
            params.setCustomerEmail(request.getCustomerEmail());
        }

        try {
            Session session = Session.create(params.build(), requestOptions);
            if (session.getUrl() == null || session.getUrl().isBlank()) {
                throw new PaymentGatewayException("Stripe did not return a checkout URL");
            }
            return new CheckoutSessionResult(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            throw new PaymentGatewayException(userSafeStripeMessage(ex), ex);
        }
    }

    @Override
    public CheckoutFulfillment retrieveCheckout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return CheckoutFulfillment.ignored();
        }
        try {
            return fromSession(retrieveExpanded(sessionId), null);
        } catch (StripeException ex) {
            throw new PaymentGatewayException(userSafeStripeMessage(ex), ex);
        }
    }

    @Override
    public CheckoutFulfillment parseWebhook(String payload, String signatureHeader) {
        if (webhookSecret.isBlank()) {
            throw new PaymentGatewayException("stripe.webhook-secret is not configured");
        }
        if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
            throw new PaymentGatewayException("Missing Stripe webhook signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new PaymentGatewayException("Invalid Stripe webhook signature", ex);
        } catch (RuntimeException ex) {
            throw new PaymentGatewayException("Invalid Stripe webhook payload", ex);
        }

        String type = event.getType();
        if (!"checkout.session.completed".equals(type)
                && !"checkout.session.async_payment_succeeded".equals(type)
                && !"checkout.session.expired".equals(type)
                && !"checkout.session.async_payment_failed".equals(type)) {
            return CheckoutFulfillment.ignored();
        }

        String sessionId = sessionIdFrom(event, payload);
        if (sessionId == null) {
            return CheckoutFulfillment.ignored();
        }
        try {
            return fromSession(retrieveExpanded(sessionId), type);
        } catch (StripeException ex) {
            throw new PaymentGatewayException(userSafeStripeMessage(ex), ex);
        }
    }

    @Override
    public void expireCheckout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Session session = Session.retrieve(sessionId, requestOptions);
            session.expire(requestOptions);
        } catch (StripeException ignored) {
            // Already expired, completed, or unknown — fulfillment handles the rest.
        }
    }

    private Session retrieveExpanded(String sessionId) throws StripeException {
        SessionRetrieveParams retrieveParams = SessionRetrieveParams.builder()
                .addExpand("payment_intent.latest_charge")
                .build();
        return Session.retrieve(sessionId, retrieveParams, requestOptions);
    }

    private CheckoutFulfillment fromSession(Session session, String eventType) {
        Long auctionId = parseLong(metadata(session, META_AUCTION_ID));
        Long payerId = parseLong(metadata(session, META_PAYER_ID));
        if (auctionId == null || payerId == null || session.getId() == null) {
            return CheckoutFulfillment.ignored();
        }

        boolean paid = "paid".equalsIgnoreCase(session.getPaymentStatus());
        boolean expired = "expired".equalsIgnoreCase(session.getStatus())
                || "checkout.session.expired".equals(eventType);
        boolean failed = "checkout.session.async_payment_failed".equals(eventType);

        if (paid) {
            return CheckoutFulfillment.succeeded(
                    auctionId,
                    payerId,
                    session.getId(),
                    paymentIntentId(session),
                    lastFour(session)
            );
        }
        if (expired) {
            return CheckoutFulfillment.expired(auctionId, payerId, session.getId());
        }
        if (failed) {
            return CheckoutFulfillment.failed(auctionId, payerId, session.getId(), "Payment failed at Stripe");
        }
        return CheckoutFulfillment.ignored();
    }

    private String sessionIdFrom(Event event, String payload) {
        if (event.getDataObjectDeserializer().getObject().orElse(null) instanceof Session session) {
            return session.getId();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode object = root.path("data").path("object");
            JsonNode id = object.get("id");
            return id == null || id.isNull() || id.asText().isBlank() ? null : id.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String metadata(Session session, String key) {
        if (session.getMetadata() == null) {
            return null;
        }
        return session.getMetadata().get(key);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String paymentIntentId(Session session) {
        if (session.getPaymentIntent() != null && !session.getPaymentIntent().isBlank()) {
            return session.getPaymentIntent();
        }
        PaymentIntent intent = session.getPaymentIntentObject();
        return intent == null ? null : intent.getId();
    }

    private static String lastFour(Session session) {
        PaymentIntent intent = session.getPaymentIntentObject();
        if (intent == null) {
            return null;
        }
        Charge charge = intent.getLatestChargeObject();
        if (charge == null || charge.getPaymentMethodDetails() == null
                || charge.getPaymentMethodDetails().getCard() == null) {
            return null;
        }
        String last4 = charge.getPaymentMethodDetails().getCard().getLast4();
        if (last4 == null || last4.isBlank()) {
            return null;
        }
        return last4.length() <= 4 ? last4 : last4.substring(last4.length() - 4);
    }

    private static String userSafeStripeMessage(StripeException ex) {
        String message = ex.getUserMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Stripe checkout could not be completed";
    }
}

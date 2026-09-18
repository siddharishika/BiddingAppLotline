package com.biddingapp.service.payment;

public class CheckoutFulfillment {

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        EXPIRED,
        IGNORED
    }

    private final Outcome outcome;
    private final Long auctionId;
    private final Long payerId;
    private final String sessionId;
    private final String transactionId;
    private final String lastFour;
    private final String failureReason;

    private CheckoutFulfillment(Outcome outcome,
                                Long auctionId,
                                Long payerId,
                                String sessionId,
                                String transactionId,
                                String lastFour,
                                String failureReason) {
        this.outcome = outcome;
        this.auctionId = auctionId;
        this.payerId = payerId;
        this.sessionId = sessionId;
        this.transactionId = transactionId;
        this.lastFour = lastFour;
        this.failureReason = failureReason;
    }

    public static CheckoutFulfillment ignored() {
        return new CheckoutFulfillment(Outcome.IGNORED, null, null, null, null, null, null);
    }

    public static CheckoutFulfillment succeeded(Long auctionId,
                                                Long payerId,
                                                String sessionId,
                                                String transactionId,
                                                String lastFour) {
        return new CheckoutFulfillment(Outcome.SUCCEEDED, auctionId, payerId, sessionId, transactionId, lastFour, null);
    }

    public static CheckoutFulfillment failed(Long auctionId,
                                             Long payerId,
                                             String sessionId,
                                             String failureReason) {
        return new CheckoutFulfillment(Outcome.FAILED, auctionId, payerId, sessionId, null, null, failureReason);
    }

    public static CheckoutFulfillment expired(Long auctionId, Long payerId, String sessionId) {
        return expired(auctionId, payerId, sessionId, null, "expired");
    }

    public static CheckoutFulfillment expired(Long auctionId,
                                              Long payerId,
                                              String sessionId,
                                              String transactionId,
                                              String stripeStatus) {
        String reason = stripeStatus == null || stripeStatus.isBlank() ? "expired" : stripeStatus;
        return new CheckoutFulfillment(Outcome.EXPIRED, auctionId, payerId, sessionId, transactionId, null, reason);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Long getAuctionId() {
        return auctionId;
    }

    public Long getPayerId() {
        return payerId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getLastFour() {
        return lastFour;
    }

    public String getFailureReason() {
        return failureReason;
    }
}

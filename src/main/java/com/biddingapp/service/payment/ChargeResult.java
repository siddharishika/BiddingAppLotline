package com.biddingapp.service.payment;

public class ChargeResult {

    private final boolean succeeded;
    private final String transactionId;
    private final String message;

    public ChargeResult(boolean succeeded, String transactionId, String message) {
        this.succeeded = succeeded;
        this.transactionId = transactionId;
        this.message = message;
    }

    public static ChargeResult success(String transactionId) {
        return new ChargeResult(true, transactionId, "Payment authorized");
    }

    public static ChargeResult declined(String message) {
        return new ChargeResult(false, null, message);
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getMessage() {
        return message;
    }
}

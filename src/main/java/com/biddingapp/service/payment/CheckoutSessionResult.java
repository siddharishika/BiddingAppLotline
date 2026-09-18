package com.biddingapp.service.payment;

public class CheckoutSessionResult {

    private final String sessionId;
    private final String url;

    public CheckoutSessionResult(String sessionId, String url) {
        this.sessionId = sessionId;
        this.url = url;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUrl() {
        return url;
    }
}

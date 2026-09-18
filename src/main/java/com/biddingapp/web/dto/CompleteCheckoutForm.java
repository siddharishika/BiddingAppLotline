package com.biddingapp.web.dto;

import jakarta.validation.constraints.NotBlank;

public class CompleteCheckoutForm {

    @NotBlank
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}

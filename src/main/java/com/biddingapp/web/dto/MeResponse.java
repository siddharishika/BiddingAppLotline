package com.biddingapp.web.dto;

public record MeResponse(boolean authenticated, Long id, String username, String role) {

    public static MeResponse anonymous() {
        return new MeResponse(false, null, null, null);
    }

    public static MeResponse of(Long id, String username, String role) {
        return new MeResponse(true, id, username, role);
    }
}

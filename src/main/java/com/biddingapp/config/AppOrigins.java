package com.biddingapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Browser origins allowed for CORS and SockJS.
 * Same-origin Render deploys do not need extra entries; keep localhost for Vite.
 * Override with env {@code APP_CORS_ORIGINS} (comma-separated).
 */
@Component
public class AppOrigins {

    private final List<String> origins;

    public AppOrigins(
            @Value("${APP_CORS_ORIGINS:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080}")
            String originsCsv) {
        this.origins = Arrays.stream(originsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public List<String> list() {
        return origins;
    }

    public String[] asArray() {
        return origins.toArray(String[]::new);
    }
}

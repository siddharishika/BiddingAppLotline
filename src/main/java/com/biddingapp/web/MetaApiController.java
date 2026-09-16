package com.biddingapp.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MetaApiController {

    @GetMapping("/api")
    public Map<String, String> api() {
        return Map.of(
                "name", "Lotline API",
                "ui", "http://localhost:5173"
        );
    }

    @GetMapping("/api/categories")
    public List<String> categories() {
        return AuctionApiController.CATEGORIES;
    }
}

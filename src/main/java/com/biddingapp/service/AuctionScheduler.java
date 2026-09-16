package com.biddingapp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuctionScheduler {

    private final AuctionService auctionService;

    public AuctionScheduler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        Instant now = Instant.now();
        auctionService.openScheduled(now);
        auctionService.closeExpired(now);
        auctionService.expireUnpaidSettlements(now);
    }
}

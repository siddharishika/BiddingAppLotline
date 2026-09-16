package com.biddingapp.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Disabled under {@code seed} so DataInitializer can finish without racing
 * closeExpired on lots that still carry a past endTime while status is updated.
 */
@Component
@Profile("!seed")
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

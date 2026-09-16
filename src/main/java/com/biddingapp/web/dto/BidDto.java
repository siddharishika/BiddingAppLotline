package com.biddingapp.web.dto;

import com.biddingapp.domain.Bid;

import java.math.BigDecimal;
import java.time.Instant;

public record BidDto(String bidderUsername, BigDecimal amount, Instant placedAt) {

    public static BidDto from(Bid bid) {
        return new BidDto(bid.getBidder().getUsername(), bid.getAmount(), bid.getPlacedAt());
    }
}

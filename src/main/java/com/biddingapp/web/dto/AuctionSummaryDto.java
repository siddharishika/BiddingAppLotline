package com.biddingapp.web.dto;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.service.AuctionService;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionSummaryDto(
        Long id,
        String title,
        String category,
        String imageUrl,
        String description,
        BigDecimal currentPrice,
        Instant startTime,
        Instant endTime,
        String status,
        long bidderCount,
        Long collectionId,
        String collectionName,
        Boolean paid,
        Instant paymentDueAt,
        BigDecimal myLastBidAmount,
        Instant myLastBidAt,
        String winnerUsername
) {
    public static AuctionSummaryDto from(AuctionItem auction) {
        return from(auction, 0, auction.getCollection() != null, null);
    }

    public static AuctionSummaryDto from(AuctionItem auction, long bidderCount) {
        return from(auction, bidderCount, auction.getCollection() != null, null);
    }

    public static AuctionSummaryDto from(AuctionItem auction, long bidderCount, boolean namedCollection) {
        return from(auction, bidderCount, namedCollection, null);
    }

    public static AuctionSummaryDto from(AuctionItem auction, long bidderCount, boolean namedCollection, Boolean paid) {
        LotCollection collection = namedCollection ? auction.getCollection() : null;
        Instant due = auction.getStatus() == AuctionStatus.SOLD ? AuctionService.paymentDueAt(auction) : null;
        return new AuctionSummaryDto(
                auction.getId(),
                auction.getTitle(),
                auction.getCategory(),
                auction.getImageUrl(),
                auction.getDescription(),
                auction.getCurrentPrice(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getStatus().name(),
                bidderCount,
                collection == null ? null : collection.getId(),
                collection == null ? null : collection.getName(),
                paid,
                due,
                null,
                null,
                auction.getWinner() == null ? null : auction.getWinner().getUsername()
        );
    }

    public AuctionSummaryDto withMyLastBid(BigDecimal amount, Instant placedAt) {
        return new AuctionSummaryDto(
                id,
                title,
                category,
                imageUrl,
                description,
                currentPrice,
                startTime,
                endTime,
                status,
                bidderCount,
                collectionId,
                collectionName,
                paid,
                paymentDueAt,
                amount,
                placedAt,
                winnerUsername
        );
    }
}

package com.biddingapp.web.dto;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.User;
import com.biddingapp.service.AuctionService;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionDetailDto(
        Long id,
        String title,
        String description,
        String category,
        String imageUrl,
        BigDecimal startingPrice,
        BigDecimal minIncrement,
        BigDecimal currentPrice,
        BigDecimal minBid,
        Instant startTime,
        Instant endTime,
        String status,
        String sellerUsername,
        String winnerUsername,
        long bidderCount,
        boolean seller,
        boolean winner,
        boolean paid,
        Instant paymentDueAt,
        Long collectionId,
        String collectionName,
        List<AuctionSummaryDto> alsoInCollection,
        BigDecimal myLastBidAmount,
        Instant myLastBidAt
) {
    public static AuctionDetailDto from(AuctionItem auction,
                                        long bidderCount,
                                        BigDecimal minBid,
                                        User current,
                                        boolean paid,
                                        boolean namedCollection,
                                        List<AuctionSummaryDto> alsoInCollection,
                                        BigDecimal myLastBidAmount,
                                        Instant myLastBidAt) {
        Long currentId = current == null ? null : current.getId();
        Long sellerId = auction.getSeller() == null ? null : auction.getSeller().getId();
        Long winnerId = auction.getWinner() == null ? null : auction.getWinner().getId();
        boolean isSeller = currentId != null && currentId.equals(sellerId);
        boolean isWinner = currentId != null && currentId.equals(winnerId);
        var collection = namedCollection ? auction.getCollection() : null;
        String winnerUsername = null;
        if (auction.getWinner() != null) {
            winnerUsername = auction.getWinner().getUsername();
        }
        Instant due = auction.getStatus() == AuctionStatus.SOLD && !paid
                ? AuctionService.paymentDueAt(auction)
                : null;
        return new AuctionDetailDto(
                auction.getId(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getCategory(),
                auction.getImageUrl(),
                auction.getStartingPrice(),
                auction.getMinIncrement(),
                auction.getCurrentPrice(),
                minBid,
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getStatus().name(),
                auction.getSeller().getUsername(),
                winnerUsername,
                bidderCount,
                isSeller,
                isWinner,
                paid,
                due,
                collection == null ? null : collection.getId(),
                collection == null ? null : collection.getName(),
                alsoInCollection == null || alsoInCollection.isEmpty() ? null : alsoInCollection,
                myLastBidAmount,
                myLastBidAt
        );
    }
}

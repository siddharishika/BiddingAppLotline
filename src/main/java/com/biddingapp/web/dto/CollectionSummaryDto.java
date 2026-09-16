package com.biddingapp.web.dto;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.service.CollectionService;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionSummaryDto(
        Long id,
        String name,
        String description,
        String sellerUsername,
        int lotCount,
        int liveLotCount,
        String imageUrl,
        List<String> imageUrls,
        String status,
        List<String> categories,
        Integer myBidCount
) {
    public static CollectionSummaryDto from(LotCollection collection, List<AuctionItem> lots) {
        return from(collection, lots, null);
    }

    public static CollectionSummaryDto from(LotCollection collection, List<AuctionItem> lots, Integer myBidCount) {
        int liveLotCount = (int) lots.stream()
                .filter(lot -> lot.getStatus() == AuctionStatus.LIVE)
                .count();
        return new CollectionSummaryDto(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.getSeller().getUsername(),
                lots.size(),
                liveLotCount,
                CollectionService.coverImage(lots),
                CollectionService.previewImages(lots),
                CollectionService.rollStatus(lots),
                CollectionService.uniqueCategories(lots),
                myBidCount
        );
    }

    public CollectionSummaryDto withMyBidCount(int myBidCount) {
        return new CollectionSummaryDto(
                id,
                name,
                description,
                sellerUsername,
                lotCount,
                liveLotCount,
                imageUrl,
                imageUrls,
                status,
                categories,
                myBidCount
        );
    }
}

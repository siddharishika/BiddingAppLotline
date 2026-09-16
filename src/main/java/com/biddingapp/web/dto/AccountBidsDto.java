package com.biddingapp.web.dto;

import java.util.List;

public record AccountBidsDto(
        List<AuctionSummaryDto> suggestions,
        List<CollectionSummaryDto> suggestedCollections,
        List<AuctionSummaryDto> placed,
        List<CollectionSummaryDto> placedCollections,
        List<AuctionSummaryDto> wins,
        List<AuctionSummaryDto> losses
) {
    public static AccountBidsDto from(
            List<AuctionSummaryDto> suggestions,
            List<CollectionSummaryDto> suggestedCollections,
            List<AuctionSummaryDto> placed,
            List<CollectionSummaryDto> placedCollections,
            List<AuctionSummaryDto> wins,
            List<AuctionSummaryDto> losses
    ) {
        return new AccountBidsDto(
                suggestions == null ? List.of() : suggestions,
                suggestedCollections == null ? List.of() : suggestedCollections,
                placed == null ? List.of() : placed,
                placedCollections == null ? List.of() : placedCollections,
                wins == null ? List.of() : wins,
                losses == null ? List.of() : losses
        );
    }
}

package com.biddingapp.web.dto;

import com.biddingapp.domain.LotCollection;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionDetailDto(
        Long id,
        String name,
        String description,
        String sellerUsername,
        int lotCount,
        String status,
        List<AuctionSummaryDto> lots
) {
    public static CollectionDetailDto from(LotCollection collection,
                                           List<AuctionSummaryDto> lots,
                                           String status) {
        return new CollectionDetailDto(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.getSeller().getUsername(),
                lots.size(),
                status,
                lots
        );
    }
}

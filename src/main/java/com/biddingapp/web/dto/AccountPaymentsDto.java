package com.biddingapp.web.dto;

import java.util.List;

public record AccountPaymentsDto(List<PaymentDto> payments, List<AuctionSummaryDto> wins) {

    public static AccountPaymentsDto from(List<PaymentDto> payments, List<AuctionSummaryDto> wins) {
        return new AccountPaymentsDto(payments, wins);
    }
}

package com.biddingapp.web.dto;

import com.biddingapp.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDto(
        Long id,
        Long auctionId,
        String auctionTitle,
        BigDecimal amount,
        String status,
        String gatewayTransactionId,
        String lastFour,
        String failureReason,
        Instant createdAt
) {
    public static PaymentDto from(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getAuction().getId(),
                payment.getAuction().getTitle(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getGatewayTransactionId(),
                payment.getLastFour(),
                payment.getFailureReason(),
                payment.getCreatedAt()
        );
    }
}

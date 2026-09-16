package com.biddingapp.websocket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class BidUpdateMessage {

    private Long auctionId;
    private BigDecimal currentPrice;
    private String bidderUsername;
    private long bidCount;
    private Instant placedAt;
    private String status;
    private BigDecimal minIncrement;
    private BigDecimal nextMinimum;
    private List<HistoryEntry> history;

    public Long getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(Long auctionId) {
        this.auctionId = auctionId;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getBidderUsername() {
        return bidderUsername;
    }

    public void setBidderUsername(String bidderUsername) {
        this.bidderUsername = bidderUsername;
    }

    public long getBidCount() {
        return bidCount;
    }

    public void setBidCount(long bidCount) {
        this.bidCount = bidCount;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getMinIncrement() {
        return minIncrement;
    }

    public void setMinIncrement(BigDecimal minIncrement) {
        this.minIncrement = minIncrement;
    }

    public BigDecimal getNextMinimum() {
        return nextMinimum;
    }

    public void setNextMinimum(BigDecimal nextMinimum) {
        this.nextMinimum = nextMinimum;
    }

    public List<HistoryEntry> getHistory() {
        return history;
    }

    public void setHistory(List<HistoryEntry> history) {
        this.history = history;
    }

    public static class HistoryEntry {
        private String bidderUsername;
        private BigDecimal amount;
        private Instant placedAt;

        public HistoryEntry() {
        }

        public HistoryEntry(String bidderUsername, BigDecimal amount, Instant placedAt) {
            this.bidderUsername = bidderUsername;
            this.amount = amount;
            this.placedAt = placedAt;
        }

        public String getBidderUsername() {
            return bidderUsername;
        }

        public void setBidderUsername(String bidderUsername) {
            this.bidderUsername = bidderUsername;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public Instant getPlacedAt() {
            return placedAt;
        }

        public void setPlacedAt(Instant placedAt) {
            this.placedAt = placedAt;
        }
    }
}

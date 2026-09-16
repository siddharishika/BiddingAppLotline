package com.biddingapp.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public class AuctionForm {

    @NotBlank
    @Size(max = 140)
    private String title;

    @NotBlank
    @Size(max = 4000)
    private String description;

    @NotBlank
    private String category;

    @Size(max = 500)
    private String imageUrl;

    @NotNull
    @DecimalMin(value = "1.00", message = "Starting price must be at least 1.00")
    private BigDecimal startingPrice;

    @NotNull
    @DecimalMin(value = "1.00", message = "Minimum increment must be at least 1.00")
    private BigDecimal minIncrement = new BigDecimal("10.00");

    @NotNull
    @Min(value = 1, message = "Auction must run for at least 1 minute")
    private Integer durationMinutes = 60;

    private Instant startTime;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public BigDecimal getMinIncrement() {
        return minIncrement;
    }

    public void setMinIncrement(BigDecimal minIncrement) {
        this.minIncrement = minIncrement;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }
}

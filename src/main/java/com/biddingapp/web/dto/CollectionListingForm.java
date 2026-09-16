package com.biddingapp.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CollectionListingForm {

    @NotBlank
    @Size(max = 140)
    private String name;

    @NotBlank
    @Size(max = 4000)
    private String description;

    private Instant startTime;

    @NotNull
    @Min(value = 1, message = "Auction must run for at least 1 minute")
    private Integer durationMinutes = 60;

    @NotNull
    @Size(min = 2, message = "A collection needs at least two lots")
    private List<@Valid AuctionForm> lots = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public List<AuctionForm> getLots() {
        return lots;
    }

    public void setLots(List<AuctionForm> lots) {
        this.lots = lots;
    }
}

package com.biddingapp.service;

public class BidRejectedException extends RuntimeException {

    public BidRejectedException(String message) {
        super(message);
    }
}

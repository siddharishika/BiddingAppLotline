package com.biddingapp.service.payment;

public interface PaymentGateway {

    ChargeResult charge(ChargeRequest request);
}

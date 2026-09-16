package com.biddingapp.service.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private SimulatedPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SimulatedPaymentGateway("sk_test_lotline_demo");
    }

    @Test
    void authorizesStripeStyleSuccessCard() {
        ChargeResult result = gateway.charge(request("4242424242424242", "sk_test_lotline_demo"));

        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.getTransactionId()).startsWith("ch_");
    }

    @Test
    void declinesBankDeclineTestCard() {
        ChargeResult result = gateway.charge(request("4000000000000002", "sk_test_lotline_demo"));

        assertThat(result.isSucceeded()).isFalse();
        assertThat(result.getMessage()).contains("declined");
    }

    @Test
    void rejectsInvalidApiKey() {
        ChargeResult result = gateway.charge(request("4242424242424242", "sk_live_wrong"));

        assertThat(result.isSucceeded()).isFalse();
        assertThat(result.getMessage()).contains("API key");
    }

    private static ChargeRequest request(String card, String apiKey) {
        return new ChargeRequest(
                apiKey,
                card,
                "12/28",
                "123",
                "Mara Cole",
                new BigDecimal("1650.00"),
                "USD",
                "Lotline auction #3"
        );
    }
}

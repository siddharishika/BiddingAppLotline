package com.biddingapp.config;

import com.biddingapp.service.payment.PaymentGateway;
import com.biddingapp.service.payment.SimulatedPaymentGateway;
import com.biddingapp.service.payment.StripeCheckoutGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(name = "lotline.payments.provider", havingValue = "simulated", matchIfMissing = true)
    PaymentGateway simulatedPaymentGateway() {
        return new SimulatedPaymentGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "lotline.payments.provider", havingValue = "stripe")
    PaymentGateway stripePaymentGateway(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret) {
        return new StripeCheckoutGateway(secretKey, webhookSecret);
    }
}

package com.biddingapp.config;

import com.biddingapp.service.payment.PaymentGateway;
import com.biddingapp.service.payment.SimulatedPaymentGateway;
import com.biddingapp.service.payment.StripeCheckoutGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "lotline.payments.provider", havingValue = "simulated", matchIfMissing = true)
    PaymentGateway simulatedPaymentGateway() {
        log.warn("Payment provider is simulated; Stripe will not receive checkout requests");
        return new SimulatedPaymentGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "lotline.payments.provider", havingValue = "stripe")
    PaymentGateway stripePaymentGateway(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret) {
        log.info("Payment provider is Stripe Checkout");
        return new StripeCheckoutGateway(secretKey, webhookSecret);
    }
}

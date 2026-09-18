package com.biddingapp.web;

import com.biddingapp.service.PaymentService;
import com.biddingapp.service.payment.CheckoutFulfillment;
import com.biddingapp.service.payment.PaymentGateway;
import com.biddingapp.service.payment.PaymentGatewayException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    public StripeWebhookController(PaymentGateway paymentGateway, PaymentService paymentService) {
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
    }

    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<Void> handle(HttpServletRequest request,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        String payload;
        try {
            payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return ResponseEntity.badRequest().build();
        }

        CheckoutFulfillment fulfillment;
        try {
            fulfillment = paymentGateway.parseWebhook(payload, signature);
        } catch (PaymentGatewayException ex) {
            log.warn("Rejected Stripe webhook: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }

        try {
            paymentService.fulfillFromGateway(fulfillment);
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            log.error("Stripe webhook fulfillment failed", ex);
            return ResponseEntity.internalServerError().build();
        }
    }
}

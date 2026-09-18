package com.biddingapp.service;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Payment;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.repository.UserRepository;
import com.biddingapp.service.payment.CheckoutFulfillment;
import com.biddingapp.service.payment.CheckoutRequest;
import com.biddingapp.service.payment.CheckoutSessionResult;
import com.biddingapp.service.payment.PaymentGateway;
import com.biddingapp.service.payment.PaymentGatewayException;
import com.biddingapp.web.dto.GatewayDto;
import com.biddingapp.web.dto.PaymentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final String currency;
    private final String publicAppUrl;

    public PaymentService(PaymentRepository paymentRepository,
                          AuctionItemRepository auctionItemRepository,
                          UserRepository userRepository,
                          PaymentGateway paymentGateway,
                          @Value("${lotline.payments.currency}") String currency,
                          @Value("${lotline.payments.public-app-url:http://localhost:5173}") String publicAppUrl) {
        this.paymentRepository = paymentRepository;
        this.auctionItemRepository = auctionItemRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.currency = currency;
        this.publicAppUrl = trimTrailingSlash(publicAppUrl);
    }

    public GatewayDto gatewayInfo() {
        return new GatewayDto(paymentGateway.providerId(), paymentGateway.usesHostedCheckout());
    }

    @Transactional(readOnly = true)
    public boolean isPaid(AuctionItem auction) {
        return paymentRepository.existsByAuctionAndStatus(auction, PaymentStatus.SUCCEEDED);
    }

    public List<PaymentDto> historyFor(User payer) {
        syncOpenCheckouts(payer);
        return paymentRepository.findByPayerOrderByCreatedAtDesc(payer).stream()
                .map(PaymentDto::from)
                .toList();
    }

    public CheckoutSessionResult startHostedCheckout(AuctionItem auction, User payer) {
        assertPayable(auction, payer);
        log.info("Starting hosted checkout for auction {} via {}", auction.getId(), paymentGateway.providerId());

        abandonUnconfirmedCheckouts(auction);

        CheckoutRequest request = new CheckoutRequest(
                auction.getId(),
                payer.getId(),
                payer.getEmail(),
                auction.getCurrentPrice(),
                currency,
                "Lotline auction #" + auction.getId() + " — " + auction.getTitle(),
                publicAppUrl + "/payments?checkout=success&session_id={CHECKOUT_SESSION_ID}",
                publicAppUrl + "/auctions/" + auction.getId() + "/pay?checkout=canceled",
                AuctionService.paymentDueAt(auction)
        );

        CheckoutSessionResult session;
        try {
            session = paymentGateway.createCheckoutSession(request);
        } catch (PaymentGatewayException ex) {
            log.warn("Stripe did not create a checkout session for auction {}: {}", auction.getId(), ex.getMessage());
            throw new PaymentFailedException(ex.getMessage());
        }

        log.info(
                "Checkout session {} created for auction {}; no payment row until Stripe confirms the charge",
                session.getSessionId(),
                auction.getId()
        );
        return session;
    }

    public Payment completeHostedCheckout(String sessionId, User payer) {
        log.info("Confirming hosted checkout {} with Stripe", sessionId);
        CheckoutFulfillment fulfillment;
        try {
            fulfillment = paymentGateway.retrieveCheckout(sessionId);
        } catch (PaymentGatewayException ex) {
            log.warn("Stripe retrieve failed for session {}: {}", sessionId, ex.getMessage());
            throw new PaymentFailedException(ex.getMessage());
        }
        if (fulfillment == null || fulfillment.getOutcome() == CheckoutFulfillment.Outcome.IGNORED) {
            log.info("Stripe has not confirmed checkout {}", sessionId);
            throw new PaymentFailedException("Stripe has not confirmed this payment yet");
        }
        if (fulfillment.getPayerId() == null || !payer.getId().equals(fulfillment.getPayerId())) {
            throw new ForbiddenException("Only the winning bidder can complete payment");
        }
        Payment payment = fulfillFromGateway(fulfillment);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            String reason = payment != null && payment.getFailureReason() != null
                    ? payment.getFailureReason()
                    : "Stripe has not confirmed this payment yet";
            throw new PaymentFailedException(reason);
        }
        return payment;
    }

    @Transactional
    public Payment fulfillFromGateway(CheckoutFulfillment fulfillment) {
        if (fulfillment == null || fulfillment.getOutcome() == CheckoutFulfillment.Outcome.IGNORED) {
            return null;
        }
        if (fulfillment.getSessionId() == null || fulfillment.getAuctionId() == null || fulfillment.getPayerId() == null) {
            return null;
        }

        log.info("Fulfilling Stripe checkout {} as {}", fulfillment.getSessionId(), fulfillment.getOutcome());

        Payment payment = paymentRepository.findByCheckoutSessionId(fulfillment.getSessionId()).orElse(null);
        AuctionItem auction = payment != null
                ? payment.getAuction()
                : auctionItemRepository.findWithSellerById(fulfillment.getAuctionId()).orElse(null);
        User payer = payment != null
                ? payment.getPayer()
                : userRepository.findById(fulfillment.getPayerId()).orElse(null);
        if (auction == null || payer == null) {
            log.warn("Stripe checkout {} referenced a missing lot or payer", fulfillment.getSessionId());
            return null;
        }

        if (payment == null) {
            if (fulfillment.getOutcome() == CheckoutFulfillment.Outcome.EXPIRED) {
                // An unused Checkout Session is not a receipt. Opening /pay or creating a session
                // is not evidence that the winner went to Stripe or paid.
                log.info("Ignoring expired Stripe session {} with no existing payment row", fulfillment.getSessionId());
                return null;
            }
            payment = new Payment();
            payment.setAuction(auction);
            payment.setPayer(payer);
            payment.setAmount(auction.getCurrentPrice());
            payment.setCheckoutSessionId(fulfillment.getSessionId());
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return payment;
        }

        if (fulfillment.getOutcome() == CheckoutFulfillment.Outcome.SUCCEEDED) {
            if (paymentRepository.existsByAuctionAndStatus(auction, PaymentStatus.SUCCEEDED)) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Lot already paid");
                return paymentRepository.save(payment);
            }
            if (!isStillPayable(auction, payer)) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Lot is no longer awaiting payment");
                return paymentRepository.save(payment);
            }
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayTransactionId(fulfillment.getTransactionId());
            payment.setLastFour(fulfillment.getLastFour());
            payment.setFailureReason(null);
            log.info("Recorded Stripe payment {} for auction {}", fulfillment.getSessionId(), auction.getId());
            return paymentRepository.save(payment);
        }

        if (fulfillment.getOutcome() == CheckoutFulfillment.Outcome.EXPIRED) {
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setFailureReason("Checkout expired");
            return paymentRepository.save(payment);
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(fulfillment.getFailureReason() == null ? "Checkout failed" : fulfillment.getFailureReason());
        return paymentRepository.save(payment);
    }

    private void abandonUnconfirmedCheckouts(AuctionItem auction) {
        for (Payment previous : paymentRepository.findByAuctionAndStatus(auction, PaymentStatus.PENDING)) {
            String previousSessionId = previous.getCheckoutSessionId();
            if (previousSessionId != null && !previousSessionId.isBlank()) {
                try {
                    CheckoutFulfillment existing = paymentGateway.retrieveCheckout(previousSessionId);
                    if (existing.getOutcome() == CheckoutFulfillment.Outcome.SUCCEEDED) {
                        fulfillFromGateway(existing);
                        throw new PaymentFailedException("This lot is already paid");
                    }
                    if (existing.getOutcome() == CheckoutFulfillment.Outcome.EXPIRED
                            || existing.getOutcome() == CheckoutFulfillment.Outcome.FAILED) {
                        fulfillFromGateway(existing);
                        continue;
                    }
                    paymentGateway.expireCheckout(previousSessionId);
                } catch (PaymentFailedException ex) {
                    throw ex;
                } catch (PaymentGatewayException ex) {
                    log.warn("Could not sync previous checkout {}: {}", previousSessionId, ex.getMessage());
                    try {
                        paymentGateway.expireCheckout(previousSessionId);
                    } catch (RuntimeException ignored) {
                        // Local pending row is still marked replaced below.
                    }
                }
            }
            if (previous.getStatus() == PaymentStatus.SUCCEEDED) {
                continue;
            }
            previous.setStatus(PaymentStatus.FAILED);
            previous.setFailureReason("Replaced by a new checkout");
            paymentRepository.save(previous);
        }
    }

    private void syncOpenCheckouts(User payer) {
        int remaining = 3;
        for (Payment payment : paymentRepository.findByPayerOrderByCreatedAtDesc(payer)) {
            if (remaining <= 0) {
                break;
            }
            if (payment.getStatus() != PaymentStatus.PENDING || payment.getCheckoutSessionId() == null
                    || payment.getCheckoutSessionId().isBlank()) {
                continue;
            }
            remaining--;
            try {
                CheckoutFulfillment fulfillment = paymentGateway.retrieveCheckout(payment.getCheckoutSessionId());
                if (fulfillment.getOutcome() == CheckoutFulfillment.Outcome.EXPIRED
                        || fulfillment.getOutcome() == CheckoutFulfillment.Outcome.SUCCEEDED
                        || fulfillment.getOutcome() == CheckoutFulfillment.Outcome.FAILED) {
                    fulfillFromGateway(fulfillment);
                } else {
                    log.info("Stripe has not confirmed pending checkout {}", payment.getCheckoutSessionId());
                }
            } catch (PaymentGatewayException ex) {
                log.warn("Leaving checkout {} pending; Stripe retrieve failed: {}",
                        payment.getCheckoutSessionId(), ex.getMessage());
            }
        }
    }

    private void assertPayable(AuctionItem auction, User payer) {
        if (!isStillPayable(auction, payer)) {
            if (auction.getStatus() != AuctionStatus.SOLD || auction.getWinner() == null) {
                throw new PaymentFailedException("This lot has no winning bid to settle");
            }
            if (!auction.getWinner().getId().equals(payer.getId())) {
                throw new PaymentFailedException("Only the winning bidder can complete payment");
            }
            if (isPaid(auction)) {
                throw new PaymentFailedException("This lot is already paid");
            }
            throw new PaymentFailedException("The 7-day payment window has closed for this lot");
        }
    }

    private boolean isStillPayable(AuctionItem auction, User payer) {
        return auction.getStatus() == AuctionStatus.SOLD
                && auction.getWinner() != null
                && auction.getWinner().getId().equals(payer.getId())
                && !isPaid(auction)
                && AuctionService.isWithinPaymentWindow(auction, Instant.now());
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:5173";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

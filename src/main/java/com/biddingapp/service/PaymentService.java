package com.biddingapp.service;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Payment;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.service.payment.ChargeRequest;
import com.biddingapp.service.payment.ChargeResult;
import com.biddingapp.service.payment.PaymentGateway;
import com.biddingapp.web.dto.PaymentDto;
import com.biddingapp.web.dto.PaymentForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate requiresNew;
    private final String apiKey;
    private final String currency;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway,
                          PlatformTransactionManager transactionManager,
                          @Value("${lotline.payments.api-key}") String apiKey,
                          @Value("${lotline.payments.currency}") String currency) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.apiKey = apiKey;
        this.currency = currency;
    }

    @Transactional(readOnly = true)
    public boolean isPaid(AuctionItem auction) {
        return paymentRepository.existsByAuctionAndStatus(auction, PaymentStatus.SUCCEEDED);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> historyFor(User payer) {
        return paymentRepository.findByPayerOrderByCreatedAtDesc(payer).stream()
                .map(PaymentDto::from)
                .toList();
    }

    @Transactional
    public Payment checkout(AuctionItem auction, User payer, PaymentForm form) {
        if (auction.getStatus() != AuctionStatus.SOLD || auction.getWinner() == null) {
            throw new PaymentFailedException("This lot has no winning bid to settle");
        }
        if (!auction.getWinner().getId().equals(payer.getId())) {
            throw new PaymentFailedException("Only the winning bidder can complete payment");
        }
        if (isPaid(auction)) {
            throw new PaymentFailedException("This lot is already paid");
        }
        if (!AuctionService.isWithinPaymentWindow(auction, Instant.now())) {
            throw new PaymentFailedException("The 7-day payment window has closed for this lot");
        }

        String digits = form.getCardNumber().replaceAll("\\s", "");
        ChargeRequest request = new ChargeRequest(
                apiKey,
                digits,
                form.getExpiry(),
                form.getCvc(),
                form.getCardholderName(),
                auction.getCurrentPrice(),
                currency,
                "Lotline auction #" + auction.getId() + " — " + auction.getTitle()
        );

        ChargeResult result = paymentGateway.charge(request);

        Payment payment = new Payment();
        payment.setAuction(auction);
        payment.setPayer(payer);
        payment.setAmount(auction.getCurrentPrice());
        payment.setLastFour(digits.substring(Math.max(0, digits.length() - 4)));

        if (!result.isSucceeded()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.getMessage());
    // Persist outside the failed checkout transaction so the row is not rolled back.
            requiresNew.execute(status -> paymentRepository.save(payment));
            throw new PaymentFailedException(result.getMessage());
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setGatewayTransactionId(result.getTransactionId());
        return paymentRepository.save(payment);
    }
}

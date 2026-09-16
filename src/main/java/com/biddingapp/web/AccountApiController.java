package com.biddingapp.web;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.User;
import com.biddingapp.service.AuctionService;
import com.biddingapp.service.BidService;
import com.biddingapp.service.CollectionService;
import com.biddingapp.service.PaymentService;
import com.biddingapp.service.UserService;
import com.biddingapp.web.dto.AccountBidsDto;
import com.biddingapp.web.dto.AccountPaymentsDto;
import com.biddingapp.web.dto.AuctionSummaryDto;
import com.biddingapp.web.dto.PaymentDto;
import com.biddingapp.web.dto.PaymentForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class AccountApiController {

    private static final int SUGGESTION_COUNT = 3;

    private final AuctionService auctionService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final BidService bidService;
    private final CollectionService collectionService;

    public AccountApiController(AuctionService auctionService,
                                PaymentService paymentService,
                                UserService userService,
                                BidService bidService,
                                CollectionService collectionService) {
        this.auctionService = auctionService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.bidService = bidService;
        this.collectionService = collectionService;
    }

    @GetMapping("/api/account/lots")
    public List<AuctionSummaryDto> myLots(Authentication authentication) {
        User user = AuthSupport.currentUser(authentication, userService);
        return bidService.toSummaries(auctionService.findBySeller(user));
    }

    @PostMapping("/api/account/lots/{id:\\d+}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawLot(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.withdrawLot(id, issuer);
    }

    @PostMapping("/api/account/lots/{id:\\d+}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenLot(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.reopenLot(id, issuer);
    }

    @DeleteMapping("/api/account/lots/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLot(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.deleteLot(id, issuer);
    }

    @PostMapping("/api/account/collections/{id:\\d+}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawCollection(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.withdrawCollection(id, issuer);
    }

    @PostMapping("/api/account/collections/{id:\\d+}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenCollection(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.reopenCollection(id, issuer);
    }

    @DeleteMapping("/api/account/collections/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.deleteCollection(id, issuer);
    }

    @GetMapping("/api/account/bids")
    public AccountBidsDto myBids(Authentication authentication) {
        User user = AuthSupport.currentUser(authentication, userService);
        return AccountBidsDto.from(
                bidService.toSummaries(auctionService.findSuggestedLive(user, SUGGESTION_COUNT), user),
                collectionService.suggestedLive(user, SUGGESTION_COUNT),
                bidService.toSummaries(auctionService.findBidOnBy(user), user),
                collectionService.bidOnBy(user),
                bidService.toWinSummaries(auctionService.findWonBy(user), user),
                bidService.toSummaries(auctionService.findLostBy(user), user)
        );
    }

    @GetMapping("/api/account/payments")
    public AccountPaymentsDto payments(Authentication authentication) {
        User user = AuthSupport.currentUser(authentication, userService);
        return AccountPaymentsDto.from(
                paymentService.historyFor(user),
                bidService.toSummaries(auctionService.findUnpaidWonBy(user))
        );
    }

    @PostMapping("/api/auctions/{id:\\d+}/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto pay(@PathVariable Long id,
                          @Valid @RequestBody PaymentForm form,
                          Authentication authentication) {
        AuctionItem auction = auctionService.require(id);
        User user = AuthSupport.currentUser(authentication, userService);
        if (auction.getWinner() == null || !auction.getWinner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the winning bidder can complete payment");
        }
        var payment = paymentService.checkout(auction, user, form);
        return PaymentDto.from(payment);
    }
}

package com.biddingapp.web;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.User;
import com.biddingapp.service.AuctionService;
import com.biddingapp.service.BidService;
import com.biddingapp.service.PaymentService;
import com.biddingapp.service.UserService;
import com.biddingapp.web.dto.AuctionDetailDto;
import com.biddingapp.web.dto.AuctionForm;
import com.biddingapp.web.dto.AuctionSummaryDto;
import com.biddingapp.web.dto.BidDto;
import com.biddingapp.web.dto.BidForm;
import com.biddingapp.web.dto.CollectionListingForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/auctions")
public class AuctionApiController {

    public static final List<String> CATEGORIES = List.of(
            "Art", "Watches", "Books", "Collectibles", "Electronics", "Fashion", "Antiques"
    );

    private final AuctionService auctionService;
    private final BidService bidService;
    private final PaymentService paymentService;
    private final UserService userService;

    public AuctionApiController(AuctionService auctionService,
                                BidService bidService,
                                PaymentService paymentService,
                                UserService userService) {
        this.auctionService = auctionService;
        this.bidService = bidService;
        this.paymentService = paymentService;
        this.userService = userService;
    }

    @GetMapping
    public List<AuctionSummaryDto> list(@RequestParam(required = false) AuctionStatus status) {
        List<AuctionItem> auctions = status == null
                ? auctionService.findAll()
                : auctionService.findByStatus(status);
        return bidService.toSummaries(auctions);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuctionSummaryDto create(@Valid @RequestBody AuctionForm form, Authentication authentication) {
        User seller = AuthSupport.currentUser(authentication, userService);
        return AuctionSummaryDto.from(auctionService.create(form, seller));
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AuctionSummaryDto> createCollection(@Valid @RequestBody CollectionListingForm form,
                                                    Authentication authentication) {
        User seller = AuthSupport.currentUser(authentication, userService);
        return bidService.toSummaries(auctionService.createCollection(form, seller));
    }

    @GetMapping("/{id:\\d+}")
    public AuctionDetailDto detail(@PathVariable Long id, Authentication authentication) {
        AuctionItem auction = auctionService.require(id);
        User current = AuthSupport.currentUser(authentication, userService);
        List<AuctionItem> collectionLots = auctionService.findCollectionLots(auction);
        boolean namedCollection = auction.getCollection() != null;
        List<AuctionItem> alsoInLots = namedCollection
                ? collectionLots.stream()
                        .filter(item -> !Objects.equals(item.getId(), auction.getId()))
                        .filter(item -> item.getStatus() != AuctionStatus.CANCELLED)
                        .toList()
                : List.of();
        List<AuctionSummaryDto> alsoIn = current == null
                ? bidService.toSummaries(alsoInLots)
                : bidService.toSummaries(alsoInLots, current);
        var latestBid = bidService.latestBy(auction, current);
        return AuctionDetailDto.from(
                auction,
                bidService.countBidders(auction),
                bidService.minimumAccepted(auction),
                current,
                paymentService.isPaid(auction),
                namedCollection,
                alsoIn,
                latestBid.map(Bid::getAmount).orElse(null),
                latestBid.map(Bid::getPlacedAt).orElse(null)
        );
    }

    @PostMapping("/{id:\\d+}/bids")
    public BidDto bid(@PathVariable Long id,
                      @Valid @RequestBody BidForm bidForm,
                      Authentication authentication) {
        User bidder = AuthSupport.currentUser(authentication, userService);
        Bid saved = bidService.placeBid(id, bidder, bidForm.getAmount());
        return new BidDto(bidder.getUsername(), saved.getAmount(), saved.getPlacedAt());
    }

    @PostMapping("/{id:\\d+}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.withdrawLot(id, issuer);
    }

    @PostMapping("/{id:\\d+}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.reopenLot(id, issuer);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.deleteLot(id, issuer);
    }
}

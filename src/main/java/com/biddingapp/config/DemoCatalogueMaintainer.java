package com.biddingapp.config;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.Payment;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.BidRepository;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps the demo catalogue usable on any calendar day: LIVE / SCHEDULED / SOLD /
 * payments / mara's bid board stay aligned to {@code Instant.now()} so a deployed
 * site does not "expire" a few hours after seed.
 */
@Component
@Order(100)
public class DemoCatalogueMaintainer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCatalogueMaintainer.class);

    /** LIVE rooms stay open at least this long after each refresh. */
    static final Duration LIVE_WINDOW = Duration.ofDays(35);
    /** Unpaid SOLD wins keep a fresh hammer time so the 7-day pay window never laps. */
    static final Duration RECENT_HAMMER = Duration.ofDays(1);

    private final AuctionItemRepository auctions;
    private final BidRepository bids;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final boolean refreshOnStartup;

    public DemoCatalogueMaintainer(AuctionItemRepository auctions,
                                   BidRepository bids,
                                   PaymentRepository payments,
                                   UserRepository users,
                                   @Value("${lotline.demo.refresh-on-startup:true}") boolean refreshOnStartup) {
        this.auctions = auctions;
        this.bids = bids;
        this.payments = payments;
        this.users = users;
        this.refreshOnStartup = refreshOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (refreshOnStartup) {
            refresh();
        }
    }

    /** Nightly top-up for long-running instances (no-op under {@code seed} if scheduler is off). */
    @Scheduled(cron = "${lotline.demo.refresh-cron:0 15 4 * * *}")
    public void nightly() {
        refresh();
    }

    @Transactional
    public void refresh() {
        Instant now = Instant.now();
        Map<String, AuctionItem> byTitle = auctions.findAll().stream()
                .filter(lot -> lot.getTitle() != null)
                .collect(Collectors.toMap(AuctionItem::getTitle, Function.identity(), (a, b) -> a, HashMap::new));
        if (byTitle.isEmpty()) {
            return;
        }

        User mara = users.findByUsername("mara").orElse(null);
        User julian = users.findByUsername("julian").orElse(null);
        User seller = users.findByUsername("seller").orElse(null);
        if (mara == null || julian == null || seller == null) {
            return;
        }

        Instant liveEnd = now.plus(LIVE_WINDOW);
        Instant liveStart = now.minus(Duration.ofDays(1));

        // —— LIVE (open for ~a month) ——
        for (String title : List.of(
                "Leica M3 body, 1955",
                "Kind of Blue — original six-eye Columbia pressing",
                "Canvas deck chair, striped",
                "Enamel picnic hamper",
                "Tide chart in a gilt frame",
                "Silver cocktail shaker, 1930s",
                "Nightclub photograph, signed",
                "Copper sauté pan",
                "Stoneware cream jug",
                "Tin porch lantern",
                "Folding garden table",
                "Rush-seat stool"
        )) {
            setWindow(byTitle.get(title), AuctionStatus.LIVE, liveStart, liveEnd, null);
        }

        // —— SCHEDULED (openings spread across the next month) ——
        schedule(byTitle.get("Danish teak lounge chair, 1960s"), now.plus(Duration.ofDays(7)), Duration.ofDays(3));
        schedule(byTitle.get("Brass reading lamp"), now.plus(Duration.ofDays(14)), Duration.ofDays(3));
        schedule(byTitle.get("Set of morocco diaries, 1920s"), now.plus(Duration.ofDays(14)), Duration.ofDays(3));
        schedule(byTitle.get("Patchwork quilt, 1930s"), now.plus(Duration.ofDays(21)), Duration.ofDays(3));
        schedule(byTitle.get("Ironstone ewer and basin"), now.plus(Duration.ofDays(21)), Duration.ofDays(3));
        schedule(byTitle.get("Oak side table, 1940s"), now.plus(Duration.ofDays(28)), Duration.ofDays(3));

        // —— SOLD (recent hammer so payment window + receipts stay valid) ——
        Instant soldStart = now.minus(Duration.ofDays(5));
        Instant soldEnd = now.minus(RECENT_HAMMER);
        setSold(byTitle.get("1968 Omega Seamaster Chronograph"), mara, "4700.00", soldStart, soldEnd);
        setSold(byTitle.get("First edition of One Hundred Years of Solitude"), mara, "980.00", soldStart, soldEnd);
        setSold(byTitle.get("Coastal study in oil, unsigned c. 1920"), julian, "1650.00", soldStart, soldEnd);
        setSold(byTitle.get("Travel poster, Cote d'Azur"), julian, "375.00", soldStart, soldEnd);

        // —— ENDED / CANCELLED (stable demo edges) ——
        setWindow(byTitle.get("Folding camp stool"), AuctionStatus.ENDED,
                now.minus(Duration.ofDays(10)), now.minus(Duration.ofDays(8)), null);
        AuctionItem valance = byTitle.get("Linen valance, unused");
        if (valance != null) {
            Instant opens = now.plus(Duration.ofDays(30));
            setWindow(valance, AuctionStatus.CANCELLED, opens, opens.plus(Duration.ofDays(2)), null);
        }

        // —— mara paddle board: placed / wins / losses / suggestions ——
        ensureBid(byTitle.get("Kind of Blue — original six-eye Columbia pressing"), mara, "380.00", now.minus(Duration.ofHours(5)));
        ensureBid(byTitle.get("Canvas deck chair, striped"), mara, "195.00", now.minus(Duration.ofHours(4)));
        ensureBid(byTitle.get("Enamel picnic hamper"), mara, "110.00", now.minus(Duration.ofHours(3)));
        ensureBid(byTitle.get("Tide chart in a gilt frame"), mara, "280.00", now.minus(Duration.ofHours(2)));
        ensureBid(byTitle.get("Silver cocktail shaker, 1930s"), mara, "340.00", now.minus(Duration.ofHours(6)));
        ensureBid(byTitle.get("Tin porch lantern"), mara, "95.00", now.minus(Duration.ofHours(1)));
        ensureBid(byTitle.get("1968 Omega Seamaster Chronograph"), mara, "4700.00", soldEnd.minus(Duration.ofHours(2)));
        ensureBid(byTitle.get("First edition of One Hundred Years of Solitude"), mara, "980.00", soldEnd.minus(Duration.ofHours(3)));
        ensureBid(byTitle.get("Coastal study in oil, unsigned c. 1920"), mara, "1550.00", soldEnd.minus(Duration.ofHours(4)));
        ensureBid(byTitle.get("Travel poster, Cote d'Azur"), mara, "350.00", soldEnd.minus(Duration.ofHours(5)));
        ensureBid(byTitle.get("Coastal study in oil, unsigned c. 1920"), julian, "1650.00", soldEnd.minus(Duration.ofHours(3)));
        ensureBid(byTitle.get("Travel poster, Cote d'Azur"), julian, "375.00", soldEnd.minus(Duration.ofHours(4)));

        // Leave some LIVE lots without mara bids so "Suggested bids/collections" stay non-empty.
        // (Rush-seat stool, copper sauté pan, stoneware jug, folding garden table.)

        // —— Payments: success + fail receipts; book stays awaiting (no SUCCEEDED) ——
        ensurePayment(byTitle.get("1968 Omega Seamaster Chronograph"), mara, PaymentStatus.SUCCEEDED,
                "4242", "ch_demo_watch", null, now.minus(Duration.ofHours(12)));
        ensurePayment(byTitle.get("First edition of One Hundred Years of Solitude"), mara, PaymentStatus.FAILED,
                "0002", null, "Card declined by issuer", now.minus(Duration.ofHours(6)));

        log.info("Demo catalogue refreshed for {}", now);
    }

    private void schedule(AuctionItem lot, Instant opens, Duration openFor) {
        if (lot == null) {
            return;
        }
        setWindow(lot, AuctionStatus.SCHEDULED, opens, opens.plus(openFor), null);
    }

    private void setSold(AuctionItem lot, User winner, String price, Instant start, Instant end) {
        if (lot == null) {
            return;
        }
        lot.setCurrentPrice(new BigDecimal(price));
        setWindow(lot, AuctionStatus.SOLD, start, end, winner);
    }

    private void setWindow(AuctionItem lot,
                           AuctionStatus status,
                           Instant start,
                           Instant end,
                           User winner) {
        if (lot == null) {
            return;
        }
        lot.setStatus(status);
        lot.setStartTime(start);
        lot.setEndTime(end);
        lot.setWinner(status == AuctionStatus.SOLD ? winner : null);
        auctions.save(lot);
    }

    private void ensureBid(AuctionItem lot, User bidder, String amount, Instant when) {
        if (lot == null || bidder == null) {
            return;
        }
        if (bids.existsByAuctionAndBidder(lot, bidder)) {
            return;
        }
        Bid bid = new Bid();
        bid.setAuction(lot);
        bid.setBidder(bidder);
        bid.setAmount(new BigDecimal(amount));
        bid.setPlacedAt(when);
        bids.save(bid);
        if (lot.getStatus() == AuctionStatus.LIVE
                && lot.getCurrentPrice().compareTo(new BigDecimal(amount)) < 0) {
            lot.setCurrentPrice(new BigDecimal(amount));
            auctions.save(lot);
        }
    }

    private void ensurePayment(AuctionItem lot,
                               User payer,
                               PaymentStatus status,
                               String lastFour,
                               String gatewayId,
                               String failureReason,
                               Instant createdAt) {
        if (lot == null || payer == null) {
            return;
        }
        if (payments.existsByAuctionAndStatus(lot, status)) {
            return;
        }
        // Avoid stacking duplicate FAILED/SUCCEEDED for the same lot when reseeding often.
        if (status == PaymentStatus.SUCCEEDED && payments.existsByAuctionAndStatus(lot, PaymentStatus.SUCCEEDED)) {
            return;
        }
        Payment payment = new Payment();
        payment.setAuction(lot);
        payment.setPayer(payer);
        payment.setAmount(lot.getCurrentPrice());
        payment.setStatus(status);
        payment.setLastFour(lastFour);
        payment.setGatewayTransactionId(gatewayId);
        payment.setFailureReason(failureReason);
        payment.setCreatedAt(createdAt);
        payments.save(payment);
    }
}

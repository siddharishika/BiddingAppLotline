package com.biddingapp.service;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.BidRepository;
import com.biddingapp.repository.LotCollectionRepository;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.web.dto.AuctionForm;
import com.biddingapp.web.dto.CollectionListingForm;
import com.biddingapp.websocket.AuctionEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuctionService {

    public static final Duration PAYMENT_WINDOW = Duration.ofDays(7);

    private final AuctionItemRepository auctionItemRepository;
    private final BidRepository bidRepository;
    private final LotCollectionRepository lotCollectionRepository;
    private final PaymentRepository paymentRepository;
    private final AuctionEventPublisher eventPublisher;

    public AuctionService(AuctionItemRepository auctionItemRepository,
                          BidRepository bidRepository,
                          LotCollectionRepository lotCollectionRepository,
                          PaymentRepository paymentRepository,
                          AuctionEventPublisher eventPublisher) {
        this.auctionItemRepository = auctionItemRepository;
        this.bidRepository = bidRepository;
        this.lotCollectionRepository = lotCollectionRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public List<AuctionItem> findAll() {
        closeExpired(Instant.now());
        return auctionItemRepository.findAllByOrderByEndTimeAsc().stream()
                .filter(item -> item.getStatus() != AuctionStatus.CANCELLED)
                .toList();
    }

    @Transactional
    public List<AuctionItem> findLive() {
        return findByStatus(AuctionStatus.LIVE);
    }

    @Transactional
    public List<AuctionItem> findByStatus(AuctionStatus status) {
        closeExpired(Instant.now());
        openScheduled(Instant.now());
        if (status == AuctionStatus.SCHEDULED) {
            return auctionItemRepository.findByStatusOrderByStartTimeAsc(status);
        }
        return auctionItemRepository.findByStatusOrderByEndTimeAsc(status);
    }

    @Transactional
    public int backfillOptimisticLocks() {
        return auctionItemRepository.backfillNullVersions();
    }

    @Transactional
    public AuctionItem require(Long id) {
        AuctionItem lot = auctionItemRepository.findWithSellerById(id)
                .orElseThrow(() -> new NotFoundException("Auction not found"));
        Instant now = Instant.now();
        if (lot.getStatus() == AuctionStatus.LIVE && lot.getEndTime() != null && !lot.getEndTime().isAfter(now)) {
            closeAuction(lot);
            publishFloor(lot);
        }
        lot.getSeller().getUsername();
        if (lot.getWinner() != null) {
            lot.getWinner().getUsername();
        }
        if (lot.getCollection() != null) {
            lot.getCollection().getName();
        }
        return lot;
    }

    @Transactional
    public List<AuctionItem> findCollectionLots(AuctionItem lot) {
        if (lot.getCollection() == null || lot.getCollection().getId() == null) {
            return List.of();
        }
        return auctionItemRepository.findByCollection_IdOrderByIdAsc(lot.getCollection().getId());
    }

    @Transactional(readOnly = true)
    public List<AuctionItem> findBySeller(User seller) {
        return auctionItemRepository.findBySellerOrderByCreatedAtDesc(seller);
    }

    @Transactional
    public List<AuctionItem> findBidOnBy(User bidder) {
        closeExpired(Instant.now());
        return auctionItemRepository.findAuctionsBidOnBy(bidder);
    }

    @Transactional(readOnly = true)
    public List<AuctionItem> findWonBy(User winner) {
        return auctionItemRepository.findByWinnerOrderByEndTimeDesc(winner);
    }

    @Transactional(readOnly = true)
    public List<AuctionItem> findUnpaidWonBy(User winner) {
        return auctionItemRepository.findUnpaidWonBy(winner, AuctionStatus.SOLD, PaymentStatus.SUCCEEDED);
    }

    @Transactional(readOnly = true)
    public List<AuctionItem> findLostBy(User bidder) {
        return auctionItemRepository.findLostByBidder(bidder.getId(), AuctionStatus.SOLD);
    }

    @Transactional
    public List<AuctionItem> findSuggestedLive(User user, int limit) {
        closeExpired(Instant.now());
        return auctionItemRepository.findByStatusAndSellerIdNotOrderByCurrentPriceAsc(
                AuctionStatus.LIVE, user.getId(), PageRequest.of(0, limit));
    }

    @Transactional
    public AuctionItem create(AuctionForm form, User seller) {
        Schedule schedule = schedule(form.getStartTime(), form.getDurationMinutes());
        LotCollection collection = new LotCollection();
        collection.setName(form.getTitle().trim());
        collection.setDescription(form.getDescription().trim());
        collection.setSeller(seller);
        LotCollection savedCollection = lotCollectionRepository.save(collection);
        return auctionItemRepository.save(buildLot(form, seller, schedule, savedCollection));
    }

    @Transactional
    public List<AuctionItem> createCollection(CollectionListingForm form, User seller) {
        if (form.getLots() == null || form.getLots().size() < 2) {
            throw new IllegalArgumentException("A collection needs at least two lots");
        }
        Schedule schedule = schedule(form.getStartTime(), form.getDurationMinutes());
        LotCollection collection = new LotCollection();
        collection.setName(form.getName().trim());
        collection.setDescription(form.getDescription() == null || form.getDescription().isBlank()
                ? form.getLots().get(0).getDescription().trim()
                : form.getDescription().trim());
        collection.setSeller(seller);
        LotCollection savedCollection = lotCollectionRepository.save(collection);
        return form.getLots().stream()
                .map(lot -> auctionItemRepository.save(buildLot(lot, seller, schedule, savedCollection)))
                .toList();
    }

    @Transactional
    public int openScheduled(Instant now) {
        List<AuctionItem> ready = auctionItemRepository
                .findByStatusAndStartTimeLessThanEqual(AuctionStatus.SCHEDULED, now);
        ready.forEach(item -> {
            item.setStatus(AuctionStatus.LIVE);
            publishFloor(item);
        });
        return ready.size();
    }

    @Transactional
    public int closeExpired(Instant now) {
        List<AuctionItem> expired = auctionItemRepository
                .findByStatusAndEndTimeLessThanEqual(AuctionStatus.LIVE, now);
        int closed = 0;
        for (AuctionItem item : expired) {
            if (item.getStatus() != AuctionStatus.LIVE) {
                continue;
            }
            closeAuction(item);
            publishFloor(item);
            closed++;
        }
        for (AuctionItem item : auctionItemRepository.findByStatusAndWinnerIsNull(AuctionStatus.SOLD)) {
            if (assignWinnerFromBids(item)) {
                publishFloor(item);
            }
        }
        return closed;
    }

    @Transactional
    public int expireUnpaidSettlements(Instant now) {
        Instant cutoff = now.minus(PAYMENT_WINDOW);
        List<AuctionItem> overdue = auctionItemRepository.findUnpaidSoldWithEndTimeLessThanEqual(
                AuctionStatus.SOLD, PaymentStatus.SUCCEEDED, cutoff);
        int forfeited = 0;
        for (AuctionItem item : overdue) {
            if (item.getStatus() != AuctionStatus.SOLD) {
                continue;
            }
            forfeitUnpaid(item);
            publishFloor(item);
            forfeited++;
        }
        return forfeited;
    }

    public static Instant paymentDueAt(AuctionItem lot) {
        if (lot == null || lot.getEndTime() == null) {
            return null;
        }
        return lot.getEndTime().plus(PAYMENT_WINDOW);
    }

    public static boolean isWithinPaymentWindow(AuctionItem lot, Instant now) {
        Instant due = paymentDueAt(lot);
        return due != null && !now.isAfter(due);
    }

    private void closeAuction(AuctionItem item) {
        if (item.getStatus() != AuctionStatus.LIVE || item.getWinner() != null) {
            return;
        }
        if (assignWinnerFromBids(item)) {
            item.setStatus(AuctionStatus.SOLD);
        } else {
            item.setStatus(AuctionStatus.ENDED);
        }
    }

    private boolean assignWinnerFromBids(AuctionItem item) {
        Bid winningBid = bidRepository.findTopByAuctionOrderByAmountDescPlacedAtAsc(item).orElse(null);
        if (winningBid == null) {
            return false;
        }
        item.setWinner(winningBid.getBidder());
        item.setCurrentPrice(winningBid.getAmount());
        return true;
    }

    private void forfeitUnpaid(AuctionItem item) {
        // Keep payment rows in the database for receipts; only clear the lot for reopen.
        bidRepository.deleteByAuction(item);
        item.setStatus(AuctionStatus.ENDED);
        item.setWinner(null);
        item.setCurrentPrice(item.getStartingPrice());
    }

    private void publishFloor(AuctionItem item) {
        try {
            eventPublisher.publishStatus(item, bidRepository.countDistinctBiddersByAuction(item));
        } catch (RuntimeException ignored) {
            // Keep the lot settled even if the live floor cannot be notified.
        }
    }

    @Transactional
    public void withdrawLot(Long id, User issuer) {
        AuctionItem lot = require(id);
        assertIssuer(lot.getSeller(), issuer);
        if (!canWithdraw(lot)) {
            throw new IllegalArgumentException("Only upcoming or unsold lots can be withdrawn");
        }
        markWithdrawn(lot);
    }

    @Transactional
    public void reopenLot(Long id, User issuer) {
        AuctionItem lot = require(id);
        assertIssuer(lot.getSeller(), issuer);
        if (lot.getStatus() != AuctionStatus.CANCELLED && lot.getStatus() != AuctionStatus.ENDED) {
            throw new IllegalArgumentException("Only withdrawn or unsold lots can be reopened");
        }
        reopen(lot);
    }

    @Transactional
    public void deleteLot(Long id, User issuer) {
        AuctionItem lot = require(id);
        assertIssuer(lot.getSeller(), issuer);
        if (lot.getStatus() != AuctionStatus.CANCELLED) {
            throw new IllegalArgumentException("Withdraw the lot before deleting it permanently");
        }
        LotCollection collection = lot.getCollection();
        removeLot(lot);
        if (collection != null && collection.getId() != null
                && auctionItemRepository.findByCollection_IdOrderByIdAsc(collection.getId()).isEmpty()) {
            lotCollectionRepository.delete(collection);
        }
    }

    @Transactional
    public void withdrawCollection(Long id, User issuer) {
        LotCollection collection = lotCollectionRepository.findWithSellerById(id)
                .orElseThrow(() -> new NotFoundException("Collection not found"));
        assertIssuer(collection.getSeller(), issuer);
        List<AuctionItem> lots = auctionItemRepository.findByCollection_IdOrderByIdAsc(collection.getId());
        if (lots.isEmpty()) {
            throw new NotFoundException("Collection not found");
        }
        if (lots.stream().anyMatch(lot -> !canWithdraw(lot))) {
            throw new IllegalArgumentException("Only collections that are still upcoming or unsold can be withdrawn");
        }
        lots.forEach(this::markWithdrawn);
    }

    @Transactional
    public void reopenCollection(Long id, User issuer) {
        LotCollection collection = lotCollectionRepository.findWithSellerById(id)
                .orElseThrow(() -> new NotFoundException("Collection not found"));
        assertIssuer(collection.getSeller(), issuer);
        List<AuctionItem> lots = auctionItemRepository.findByCollection_IdOrderByIdAsc(collection.getId());
        if (lots.isEmpty() || lots.stream().anyMatch(lot -> lot.getStatus() != AuctionStatus.CANCELLED)) {
            throw new IllegalArgumentException("Only withdrawn collections can be reopened");
        }
        lots.forEach(this::reopen);
    }

    @Transactional
    public void deleteCollection(Long id, User issuer) {
        LotCollection collection = lotCollectionRepository.findWithSellerById(id)
                .orElseThrow(() -> new NotFoundException("Collection not found"));
        assertIssuer(collection.getSeller(), issuer);
        List<AuctionItem> lots = auctionItemRepository.findByCollection_IdOrderByIdAsc(collection.getId());
        if (lots.stream().anyMatch(lot -> lot.getStatus() != AuctionStatus.CANCELLED)) {
            throw new IllegalArgumentException("Withdraw the collection before deleting it permanently");
        }
        lots.forEach(this::removeLot);
        lotCollectionRepository.delete(collection);
    }

    private static boolean canWithdraw(AuctionItem lot) {
        return lot.getStatus() == AuctionStatus.SCHEDULED || lot.getStatus() == AuctionStatus.ENDED;
    }

    private void markWithdrawn(AuctionItem lot) {
        lot.setStatus(AuctionStatus.CANCELLED);
        lot.setWinner(null);
        publishFloor(lot);
    }

    private void reopen(AuctionItem lot) {
        Instant now = Instant.now();
        Duration duration = Duration.between(lot.getStartTime(), lot.getEndTime());
        if (duration.isNegative() || duration.isZero()) {
            duration = Duration.ofHours(1);
        }
        lot.setStartTime(now);
        lot.setEndTime(now.plus(duration));
        lot.setWinner(null);
        lot.setCurrentPrice(lot.getStartingPrice());
        lot.setStatus(AuctionStatus.LIVE);
        publishFloor(lot);
    }

    private void removeLot(AuctionItem lot) {
        paymentRepository.deleteByAuction(lot);
        bidRepository.deleteByAuction(lot);
        auctionItemRepository.delete(lot);
    }

    private static void assertIssuer(User owner, User current) {
        if (owner == null || current == null || !owner.getId().equals(current.getId())) {
            throw new ForbiddenException("Only the issuer can withdraw this listing");
        }
    }

    private AuctionItem buildLot(AuctionForm form, User seller, Schedule schedule, LotCollection collection) {
        AuctionItem item = new AuctionItem();
        item.setTitle(form.getTitle().trim());
        item.setDescription(form.getDescription().trim());
        item.setCategory(form.getCategory());
        item.setImageUrl(blankToNull(form.getImageUrl()));
        item.setStartingPrice(form.getStartingPrice());
        item.setMinIncrement(form.getMinIncrement());
        item.setCurrentPrice(form.getStartingPrice());
        item.setSeller(seller);
        item.setStartTime(schedule.start);
        item.setEndTime(schedule.end);
        item.setStatus(schedule.start.isAfter(schedule.now) ? AuctionStatus.SCHEDULED : AuctionStatus.LIVE);
        item.setCollection(collection);
        return item;
    }

    private static Schedule schedule(Instant requestedStart, Integer durationMinutes) {
        Instant now = Instant.now();
        Instant start = requestedStart == null ? now : requestedStart;
        if (start.isAfter(now.plus(Duration.ofDays(365)))) {
            throw new IllegalArgumentException("Opening time cannot be more than a year from now");
        }
        if (!start.isAfter(now)) {
            start = now;
        }
        int duration = durationMinutes == null ? 60 : durationMinutes;
        return new Schedule(now, start, start.plus(Duration.ofMinutes(duration)));
    }

    private record Schedule(Instant now, Instant start, Instant end) {
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

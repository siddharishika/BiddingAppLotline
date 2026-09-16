package com.biddingapp.service;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.BidRepository;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.web.dto.AuctionSummaryDto;
import com.biddingapp.websocket.AuctionEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class BidService {

    private final AuctionItemRepository auctionItemRepository;
    private final BidRepository bidRepository;
    private final PaymentRepository paymentRepository;
    private final AuctionEventPublisher eventPublisher;

    public BidService(AuctionItemRepository auctionItemRepository,
                      BidRepository bidRepository,
                      PaymentRepository paymentRepository,
                      AuctionEventPublisher eventPublisher) {
        this.auctionItemRepository = auctionItemRepository;
        this.bidRepository = bidRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Bid> history(AuctionItem auction) {
        return bidRepository.findByAuctionOrderByPlacedAtDesc(auction);
    }

    @Transactional(readOnly = true)
    public long count(AuctionItem auction) {
        return bidRepository.countByAuction(auction);
    }

    @Transactional(readOnly = true)
    public long countBidders(AuctionItem auction) {
        return bidRepository.countDistinctBiddersByAuction(auction);
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> toSummaries(List<AuctionItem> auctions) {
        if (auctions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = auctions.stream().map(AuctionItem::getId).toList();
        Map<Long, Long> counts = bidderCounts(ids);
        return auctions.stream()
                .map(auction -> AuctionSummaryDto.from(auction, bidderCount(counts, auction.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> toSummaries(List<AuctionItem> auctions, User bidder) {
        return withMyLastBids(toSummaries(auctions), bidder);
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> toWinSummaries(List<AuctionItem> auctions) {
        if (auctions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = auctions.stream().map(AuctionItem::getId).toList();
        Map<Long, Long> counts = bidderCounts(ids);
        Set<Long> paidIds = new HashSet<>(paymentRepository.findAuctionIdsByStatus(PaymentStatus.SUCCEEDED, ids));
        return auctions.stream()
                .map(auction -> AuctionSummaryDto.from(
                        auction,
                        bidderCount(counts, auction.getId()),
                        auction.getCollection() != null,
                        paidIds.contains(auction.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> toWinSummaries(List<AuctionItem> auctions, User bidder) {
        return withMyLastBids(toWinSummaries(auctions), bidder);
    }

    private List<AuctionSummaryDto> withMyLastBids(List<AuctionSummaryDto> summaries, User bidder) {
        if (summaries.isEmpty() || bidder == null) {
            return summaries;
        }
        List<Long> ids = summaries.stream().map(AuctionSummaryDto::id).toList();
        Map<Long, Bid> latest = new HashMap<>();
        for (Bid bid : bidRepository.findByBidderAndAuctionIdsOrderByPlacedAtDesc(bidder, ids)) {
            latest.putIfAbsent(bid.getAuction().getId(), bid);
        }
        return summaries.stream()
                .map(summary -> {
                    Bid bid = latest.get(summary.id());
                    return bid == null ? summary : summary.withMyLastBid(bid.getAmount(), bid.getPlacedAt());
                })
                .toList();
    }

    private static long bidderCount(Map<Long, Long> counts, Long id) {
        Long count = counts.get(id);
        return count == null ? 0L : count;
    }

    private Map<Long, Long> bidderCounts(List<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return counts;
        }
        for (Object[] row : bidRepository.countDistinctBiddersByAuctionIds(ids)) {
            if (row == null || row[0] == null || row[1] == null) {
                continue;
            }
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Optional<Bid> latestBy(AuctionItem auction, User bidder) {
        if (bidder == null) {
            return Optional.empty();
        }
        return bidRepository.findTopByAuctionAndBidderOrderByPlacedAtDesc(auction, bidder);
    }

    @Transactional
    public Bid placeBid(Long auctionId, User bidder, BigDecimal amount) {
        AuctionItem auction = auctionItemRepository.findWithSellerByIdForUpdate(auctionId)
                .orElseThrow(() -> new NotFoundException("Auction not found"));

        Instant now = Instant.now();
        if (!auction.isAcceptingBids(now)) {
            throw new BidRejectedException("This auction is not accepting bids");
        }
        if (auction.getSeller().getId().equals(bidder.getId())) {
            throw new BidRejectedException("Sellers cannot bid on their own lots");
        }
        if (amount == null) {
            throw new BidRejectedException("Enter a bid amount");
        }

        BigDecimal minimum = minimumAccepted(auction);
        if (amount.compareTo(minimum) < 0) {
            throw new BidRejectedException("Bid must be at least $" + minimum);
        }

        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setAmount(amount);
        bid.setPlacedAt(now);
        Bid saved = bidRepository.save(bid);

        auction.setCurrentPrice(amount);
        auctionItemRepository.save(auction);

        List<Bid> bids = bidRepository.findByAuctionOrderByPlacedAtDesc(auction);
        eventPublisher.publishBid(auction, saved, bids);
        return saved;
    }

    public BigDecimal minimumAccepted(AuctionItem auction) {
        return auction.getCurrentPrice().add(auction.getMinIncrement());
    }
}

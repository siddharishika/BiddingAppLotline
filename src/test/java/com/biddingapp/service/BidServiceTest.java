package com.biddingapp.service;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.BidRepository;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.websocket.AuctionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuctionEventPublisher eventPublisher;

    @InjectMocks
    private BidService bidService;

    private User seller;
    private User bidder;
    private AuctionItem liveLot;

    @BeforeEach
    void setUp() {
        seller = user(1L, "seller");
        bidder = user(2L, "mara");
        liveLot = liveAuction(seller);
    }

    @Test
    void rejectsBidFromSeller() {
        when(auctionItemRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(liveLot));

        assertThatThrownBy(() -> bidService.placeBid(10L, seller, new BigDecimal("5000.00")))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("Sellers cannot bid");

        verify(bidRepository, never()).save(any());
    }

    @Test
    void rejectsBidBelowMinimumIncrement() {
        liveLot.setCurrentPrice(new BigDecimal("4700.00"));
        when(auctionItemRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(liveLot));

        assertThatThrownBy(() -> bidService.placeBid(10L, bidder, new BigDecimal("4700.00")))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("at least $4800.00");
    }

    @Test
    void rejectsBidWhenAuctionIsClosed() {
        liveLot.setStatus(AuctionStatus.SOLD);
        when(auctionItemRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(liveLot));

        assertThatThrownBy(() -> bidService.placeBid(10L, bidder, new BigDecimal("5000.00")))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("not accepting bids");
    }

    @Test
    void rejectsOpeningBidAtCurrentPrice() {
        when(auctionItemRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(liveLot));

        assertThatThrownBy(() -> bidService.placeBid(10L, bidder, new BigDecimal("4200.00")))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("at least $4300.00");
    }

    @Test
    void acceptsOpeningBidAtCurrentPlusIncrementAndPublishesUpdate() {
        when(auctionItemRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(liveLot));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bidRepository.findByAuctionOrderByPlacedAtDesc(liveLot)).thenReturn(List.of());

        Bid saved = bidService.placeBid(10L, bidder, new BigDecimal("4300.00"));

        assertThat(saved.getAmount()).isEqualByComparingTo("4300.00");
        assertThat(liveLot.getCurrentPrice()).isEqualByComparingTo("4300.00");
        verify(eventPublisher).publishBid(any(), any(), any());
        ArgumentCaptor<AuctionItem> captor = ArgumentCaptor.forClass(AuctionItem.class);
        verify(auctionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo("4300.00");
    }

    @Test
    void toSummariesUsesUniqueBidderCounts() {
        AuctionItem first = liveAuction(seller);
        first.setId(10L);
        AuctionItem second = liveAuction(seller);
        second.setId(11L);
        when(bidRepository.countDistinctBiddersByAuctionIds(List.of(10L, 11L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 3L}));

        var summaries = bidService.toSummaries(List.of(first, second));

        assertThat(summaries).extracting("id", "bidderCount")
                .containsExactly(tuple(10L, 3L), tuple(11L, 0L));
    }

    @Test
    void toSummariesKeepsCollectionWhenThePostingIsASingleLot() {
        LotCollection collection = new LotCollection();
        collection.setId(4L);
        collection.setName("Teak chair");
        AuctionItem only = liveAuction(seller);
        only.setId(10L);
        only.setCategory("Art");
        only.setCollection(collection);
        when(bidRepository.countDistinctBiddersByAuctionIds(List.of(10L))).thenReturn(List.of());

        var summaries = bidService.toSummaries(List.of(only));

        assertThat(summaries.get(0).collectionId()).isEqualTo(4L);
        assertThat(summaries.get(0).collectionName()).isEqualTo("Teak chair");
        assertThat(summaries.get(0).category()).isEqualTo("Art");
    }

    @Test
    void toSummariesKeepsCollectionWhenTwoOrMoreLotsShareIt() {
        LotCollection collection = new LotCollection();
        collection.setId(4L);
        collection.setName("A weekend by the sea");
        AuctionItem lot = liveAuction(seller);
        lot.setId(10L);
        lot.setCategory("Antiques");
        lot.setCollection(collection);
        when(bidRepository.countDistinctBiddersByAuctionIds(List.of(10L))).thenReturn(List.of());

        var summaries = bidService.toSummaries(List.of(lot));

        assertThat(summaries.get(0).collectionId()).isEqualTo(4L);
        assertThat(summaries.get(0).collectionName()).isEqualTo("A weekend by the sea");
    }

    @Test
    void toSummariesForBidderAttachesThatUsersLatestBid() {
        AuctionItem lot = liveAuction(seller);
        lot.setId(10L);
        Bid older = new Bid();
        older.setAuction(lot);
        older.setAmount(new BigDecimal("4300.00"));
        older.setPlacedAt(Instant.parse("2026-09-15T05:00:00Z"));
        Bid latest = new Bid();
        latest.setAuction(lot);
        latest.setAmount(new BigDecimal("4700.00"));
        latest.setPlacedAt(Instant.parse("2026-09-15T05:10:00Z"));
        when(bidRepository.countDistinctBiddersByAuctionIds(List.of(10L))).thenReturn(List.of());
        when(bidRepository.findByBidderAndAuctionIdsOrderByPlacedAtDesc(bidder, List.of(10L)))
                .thenReturn(List.of(latest, older));

        var summaries = bidService.toSummaries(List.of(lot), bidder);

        assertThat(summaries.get(0).myLastBidAmount()).isEqualByComparingTo("4700.00");
        assertThat(summaries.get(0).myLastBidAt()).isEqualTo(latest.getPlacedAt());
        assertThat(summaries.get(0).currentPrice()).isEqualByComparingTo("4200.00");
    }

    @Test
    void toSummariesIncludesWinnerUsernameOnASoldLot() {
        User winner = new User();
        winner.setUsername("julian");
        AuctionItem lot = liveAuction(seller);
        lot.setId(10L);
        lot.setStatus(AuctionStatus.SOLD);
        lot.setCurrentPrice(new BigDecimal("375.00"));
        lot.setWinner(winner);
        when(bidRepository.countDistinctBiddersByAuctionIds(List.of(10L))).thenReturn(List.of());

        var summaries = bidService.toSummaries(List.of(lot));

        assertThat(summaries.get(0).winnerUsername()).isEqualTo("julian");
        assertThat(summaries.get(0).currentPrice()).isEqualByComparingTo("375.00");
    }

    @Test
    void latestByReturnsThatUsersMostRecentBidOnTheLot() {
        Bid latest = new Bid();
        latest.setAuction(liveLot);
        latest.setAmount(new BigDecimal("4700.00"));
        latest.setPlacedAt(Instant.parse("2026-09-15T05:10:00Z"));
        when(bidRepository.findTopByAuctionAndBidderOrderByPlacedAtDesc(liveLot, bidder))
                .thenReturn(Optional.of(latest));

        assertThat(bidService.latestBy(liveLot, bidder).orElseThrow().getAmount())
                .isEqualByComparingTo("4700.00");
        assertThat(bidService.latestBy(liveLot, null)).isEmpty();
    }

    @Test
    void minimumAcceptedIsCurrentPricePlusIncrement() {
        liveLot.setCurrentPrice(new BigDecimal("600.00"));
        liveLot.setMinIncrement(new BigDecimal("25.00"));

        assertThat(bidService.minimumAccepted(liveLot)).isEqualByComparingTo("625.00");
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static AuctionItem liveAuction(User seller) {
        AuctionItem item = new AuctionItem();
        item.setId(10L);
        item.setTitle("Test lot");
        item.setStartingPrice(new BigDecimal("4200.00"));
        item.setMinIncrement(new BigDecimal("100.00"));
        item.setCurrentPrice(new BigDecimal("4200.00"));
        item.setSeller(seller);
        item.setStatus(AuctionStatus.LIVE);
        item.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        item.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));
        return item;
    }
}

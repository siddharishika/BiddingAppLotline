package com.biddingapp.service;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.User;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.BidRepository;
import com.biddingapp.repository.LotCollectionRepository;
import com.biddingapp.repository.PaymentRepository;
import com.biddingapp.websocket.AuctionEventPublisher;
import com.biddingapp.web.dto.AuctionForm;
import com.biddingapp.web.dto.AuctionSummaryDto;
import com.biddingapp.web.dto.CollectionListingForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private LotCollectionRepository lotCollectionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuctionEventPublisher eventPublisher;

    @InjectMocks
    private AuctionService auctionService;

    @BeforeEach
    void stubCollectionSave() {
        lenient().when(lotCollectionRepository.save(any(LotCollection.class))).thenAnswer(invocation -> {
            LotCollection collection = invocation.getArgument(0);
            if (collection.getId() == null) {
                collection.setId(1L);
            }
            return collection;
        });
        lenient().when(auctionItemRepository.findByStatusAndWinnerIsNull(AuctionStatus.SOLD))
                .thenReturn(List.of());
        lenient().when(auctionItemRepository.findByStatusAndEndTimeLessThanEqual(any(), any()))
                .thenReturn(List.of());
        lenient().when(auctionItemRepository.findUnpaidSoldWithEndTimeLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void closeExpiredMarksSoldAndAssignsHighestBidder() {
        User winner = new User();
        winner.setId(2L);
        winner.setUsername("julian");

        AuctionItem lot = new AuctionItem();
        lot.setId(3L);
        lot.setStatus(AuctionStatus.LIVE);
        lot.setEndTime(Instant.now().minus(1, ChronoUnit.MINUTES));
        lot.setCurrentPrice(new BigDecimal("1500.00"));

        Bid winningBid = new Bid();
        winningBid.setBidder(winner);
        winningBid.setAmount(new BigDecimal("1650.00"));

        Instant now = Instant.now();
        when(auctionItemRepository.findByStatusAndEndTimeLessThanEqual(AuctionStatus.LIVE, now))
                .thenReturn(List.of(lot));
        when(bidRepository.findTopByAuctionOrderByAmountDescPlacedAtAsc(lot))
                .thenReturn(Optional.of(winningBid));
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(1L);

        int closed = auctionService.closeExpired(now);

        assertThat(closed).isEqualTo(1);
        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.SOLD);
        assertThat(lot.getWinner()).isEqualTo(winner);
        assertThat(lot.getCurrentPrice()).isEqualByComparingTo("1650.00");
        verify(eventPublisher).publishStatus(lot, 1L);
    }

    @Test
    void closeExpiredWithNoBidsEndsUnsold() {
        AuctionItem lot = new AuctionItem();
        lot.setId(8L);
        lot.setStatus(AuctionStatus.LIVE);
        lot.setEndTime(Instant.now().minus(1, ChronoUnit.MINUTES));

        Instant now = Instant.now();
        when(auctionItemRepository.findByStatusAndEndTimeLessThanEqual(AuctionStatus.LIVE, now))
                .thenReturn(List.of(lot));
        when(bidRepository.findTopByAuctionOrderByAmountDescPlacedAtAsc(lot))
                .thenReturn(Optional.empty());
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(0L);

        auctionService.closeExpired(now);

        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(lot.getWinner()).isNull();
    }

    @Test
    void closeExpiredDoesNotReplaceAnExistingWinner() {
        User first = new User();
        first.setId(2L);
        AuctionItem lot = new AuctionItem();
        lot.setId(3L);
        lot.setStatus(AuctionStatus.SOLD);
        lot.setWinner(first);
        lot.setEndTime(Instant.now().minus(1, ChronoUnit.MINUTES));
        Instant now = Instant.now();
        when(auctionItemRepository.findByStatusAndEndTimeLessThanEqual(AuctionStatus.LIVE, now))
                .thenReturn(List.of(lot));

        auctionService.closeExpired(now);

        assertThat(lot.getWinner()).isEqualTo(first);
        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.SOLD);
        verify(bidRepository, never()).findTopByAuctionOrderByAmountDescPlacedAtAsc(any());
    }

    @Test
    void withdrawLotMarksAScheduledListingCancelled() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem lot = upcomingLot(seller);
        when(auctionItemRepository.findWithSellerById(10L)).thenReturn(Optional.of(lot));
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(0L);

        auctionService.withdrawLot(10L, seller);

        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionItemRepository, never()).delete(any(AuctionItem.class));
        verify(eventPublisher).publishStatus(lot, 0L);
    }

    @Test
    void withdrawLotAllowsAnUnsoldListing() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem lot = upcomingLot(seller);
        lot.setStatus(AuctionStatus.ENDED);
        when(auctionItemRepository.findWithSellerById(10L)).thenReturn(Optional.of(lot));
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(0L);

        auctionService.withdrawLot(10L, seller);

        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
    }

    @Test
    void withdrawLotRejectsALiveListing() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem lot = upcomingLot(seller);
        lot.setStatus(AuctionStatus.LIVE);
        when(auctionItemRepository.findWithSellerById(10L)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> auctionService.withdrawLot(10L, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upcoming or unsold");
        verify(auctionItemRepository, never()).delete(any(AuctionItem.class));
    }

    @Test
    void reopenLotPutsAWithdrawnListingBackOnTheBlock() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem lot = upcomingLot(seller);
        lot.setStatus(AuctionStatus.CANCELLED);
        lot.setStartingPrice(new BigDecimal("220.00"));
        lot.setCurrentPrice(new BigDecimal("220.00"));
        when(auctionItemRepository.findWithSellerById(10L)).thenReturn(Optional.of(lot));
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(0L);

        auctionService.reopenLot(10L, seller);

        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.LIVE);
        assertThat(lot.getCurrentPrice()).isEqualByComparingTo("220.00");
        assertThat(lot.getEndTime()).isAfter(lot.getStartTime());
    }

    @Test
    void reopenLotPutsAnUnsoldListingBackOnTheBlock() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem lot = upcomingLot(seller);
        lot.setStatus(AuctionStatus.ENDED);
        lot.setStartingPrice(new BigDecimal("40.00"));
        lot.setCurrentPrice(new BigDecimal("40.00"));
        when(auctionItemRepository.findWithSellerById(10L)).thenReturn(Optional.of(lot));
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(0L);

        auctionService.reopenLot(10L, seller);

        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.LIVE);
        assertThat(lot.getWinner()).isNull();
    }

    @Test
    void expireUnpaidSettlementsReturnsSoldLotsToUnsold() {
        User winner = new User();
        winner.setId(2L);
        AuctionItem lot = new AuctionItem();
        lot.setId(8L);
        lot.setStatus(AuctionStatus.SOLD);
        lot.setWinner(winner);
        lot.setEndTime(Instant.now().minus(8, ChronoUnit.DAYS));
        lot.setCurrentPrice(new BigDecimal("110.00"));

        Instant now = Instant.now();
        when(auctionItemRepository.findUnpaidSoldWithEndTimeLessThanEqual(
                eq(AuctionStatus.SOLD), eq(PaymentStatus.SUCCEEDED), any()))
                .thenReturn(List.of(lot));
        when(bidRepository.countDistinctBiddersByAuction(lot)).thenReturn(1L);

        int forfeited = auctionService.expireUnpaidSettlements(now);

        assertThat(forfeited).isEqualTo(1);
        assertThat(lot.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(lot.getWinner()).isNull();
        verify(paymentRepository, never()).deleteByAuction(lot);
        verify(bidRepository).deleteByAuction(lot);
        verify(eventPublisher).publishStatus(lot, 1L);
    }

    @Test
    void deleteLotRemovesAWithdrawnListing() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem lot = upcomingLot(seller);
        lot.setStatus(AuctionStatus.CANCELLED);
        when(auctionItemRepository.findWithSellerById(10L)).thenReturn(Optional.of(lot));
        when(auctionItemRepository.findByCollection_IdOrderByIdAsc(9L)).thenReturn(List.of());

        auctionService.deleteLot(10L, seller);

        verify(bidRepository).deleteByAuction(lot);
        verify(paymentRepository).deleteByAuction(lot);
        verify(auctionItemRepository).delete(lot);
        verify(lotCollectionRepository).delete(lot.getCollection());
    }

    @Test
    void withdrawCollectionMarksEveryUpcomingLotCancelled() {
        User seller = new User();
        seller.setId(1L);
        AuctionItem first = upcomingLot(seller);
        AuctionItem second = upcomingLot(seller);
        second.setId(11L);
        second.setTitle("Coastal oil");
        when(lotCollectionRepository.findWithSellerById(9L)).thenReturn(Optional.of(first.getCollection()));
        when(auctionItemRepository.findByCollection_IdOrderByIdAsc(9L)).thenReturn(List.of(first, second));
        when(bidRepository.countDistinctBiddersByAuction(any())).thenReturn(0L);

        auctionService.withdrawCollection(9L, seller);

        assertThat(first.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionItemRepository, never()).delete(any(AuctionItem.class));
    }

    @Test
    void findUnpaidWonByAsksRepositoryForLotsWithoutSuccessfulPayment() {
        User winner = new User();
        winner.setId(2L);
        AuctionItem unpaid = new AuctionItem();
        unpaid.setId(9L);
        when(auctionItemRepository.findUnpaidWonBy(winner, AuctionStatus.SOLD, PaymentStatus.SUCCEEDED))
                .thenReturn(List.of(unpaid));

        List<AuctionItem> result = auctionService.findUnpaidWonBy(winner);

        assertThat(result).containsExactly(unpaid);
    }

    @Test
    void createWithFutureStartTimeSchedulesUpcomingLot() {
        Instant opensAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        AuctionForm form = listingForm();
        form.setStartTime(opensAt);
        User seller = new User();
        seller.setId(1L);
        when(auctionItemRepository.save(any(AuctionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItem saved = auctionService.create(form, seller);

        assertThat(saved.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        assertThat(saved.getStartTime()).isEqualTo(opensAt);
        assertThat(saved.getEndTime()).isEqualTo(opensAt.plus(60, ChronoUnit.MINUTES));
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("600.00");
    }

    @Test
    void createWithoutStartTimeOpensLiveImmediately() {
        AuctionForm form = listingForm();
        User seller = new User();
        seller.setId(1L);
        when(auctionItemRepository.save(any(AuctionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItem saved = auctionService.create(form, seller);

        assertThat(saved.getStatus()).isEqualTo(AuctionStatus.LIVE);
        assertThat(saved.getStartTime()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
    }

    @Test
    void createWithPastStartTimeOpensLiveNow() {
        AuctionForm form = listingForm();
        form.setStartTime(Instant.now().minus(10, ChronoUnit.MINUTES));
        User seller = new User();
        seller.setId(1L);
        when(auctionItemRepository.save(any(AuctionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItem saved = auctionService.create(form, seller);

        assertThat(saved.getStatus()).isEqualTo(AuctionStatus.LIVE);
        assertThat(saved.getStartTime()).isAfter(Instant.now().minusSeconds(2));
    }

    @Test
    void createCollectionGroupsLotsUnderOneTimeCategory() {
        CollectionListingForm form = new CollectionListingForm();
        form.setName("A weekend by the sea");
        form.setDescription("Four sentences about a coastal house and why these lots were used together on the same weekends, not bought as a matching set from a catalogue.");
        form.setDurationMinutes(60);
        form.setLots(List.of(listingForm(), listingForm()));
        form.getLots().get(1).setTitle("Coastal oil");
        User seller = new User();
        seller.setId(1L);
        when(lotCollectionRepository.save(any(LotCollection.class))).thenAnswer(invocation -> {
            LotCollection collection = invocation.getArgument(0);
            collection.setId(4L);
            return collection;
        });
        when(auctionItemRepository.save(any(AuctionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AuctionItem> saved = auctionService.createCollection(form, seller);

        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(lot -> lot.getCollection() != null && lot.getCollection().getId().equals(4L));
        assertThat(saved.get(0).getCollection().getName()).isEqualTo("A weekend by the sea");
        assertThat(saved.get(0).getCollection().getDescription()).contains("coastal house");
        assertThat(saved.get(0).getTitle()).isEqualTo("Teak chair");
        assertThat(saved.get(1).getTitle()).isEqualTo("Coastal oil");
    }

    @Test
    void findCollectionLotsIsEmptyWhenLotHasNoCollection() {
        AuctionItem lot = new AuctionItem();
        lot.setId(1L);

        assertThat(auctionService.findCollectionLots(lot)).isEmpty();
    }

    @Test
    void createAssignsACollectionNamedAfterTheLot() {
        AuctionForm form = listingForm();
        User seller = new User();
        seller.setId(1L);
        when(auctionItemRepository.save(any(AuctionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItem saved = auctionService.create(form, seller);

        assertThat(saved.getCollection()).isNotNull();
        assertThat(saved.getCollection().getName()).isEqualTo("Teak chair");
        assertThat(saved.getCollection().getDescription()).isEqualTo("A lounge chair");
        assertThat(saved.getCollection().getSeller()).isEqualTo(seller);
    }

    @Test
    void summaryKeepsCollectionLayerForASingleLot() {
        AuctionItem lot = new AuctionItem();
        lot.setId(1L);
        lot.setTitle("Teak chair");
        lot.setCategory("Antiques");
        lot.setDescription("A Danish teak lounge chair from a quiet house. The frame is unmarked. The seat was recently reupholstered. Offered as furniture to use.");
        lot.setCurrentPrice(new BigDecimal("600.00"));
        lot.setStartTime(Instant.parse("2026-09-06T12:00:00Z"));
        lot.setEndTime(Instant.parse("2026-09-06T13:00:00Z"));
        lot.setStatus(AuctionStatus.LIVE);
        LotCollection collection = new LotCollection();
        collection.setId(9L);
        collection.setName("Teak chair");
        lot.setCollection(collection);

        AuctionSummaryDto dto = AuctionSummaryDto.from(lot, 2);

        assertThat(dto.collectionId()).isEqualTo(9L);
        assertThat(dto.collectionName()).isEqualTo("Teak chair");
        assertThat(dto.category()).isEqualTo("Antiques");
        assertThat(dto.description()).contains("Danish teak lounge chair");
        assertThat(dto.winnerUsername()).isNull();
    }

    @Test
    void summaryNamesTheWinnerOnASoldLot() {
        User winner = new User();
        winner.setUsername("julian");
        AuctionItem lot = new AuctionItem();
        lot.setId(1L);
        lot.setTitle("Travel poster, Cote d'Azur");
        lot.setCategory("Art");
        lot.setDescription("A mid-century travel poster.");
        lot.setCurrentPrice(new BigDecimal("375.00"));
        lot.setStartTime(Instant.parse("2026-09-06T12:00:00Z"));
        lot.setEndTime(Instant.parse("2026-09-06T13:00:00Z"));
        lot.setStatus(AuctionStatus.SOLD);
        lot.setWinner(winner);

        AuctionSummaryDto dto = AuctionSummaryDto.from(lot, 2);

        assertThat(dto.winnerUsername()).isEqualTo("julian");
        assertThat(dto.currentPrice()).isEqualByComparingTo("375.00");
    }

    private static AuctionForm listingForm() {
        AuctionForm form = new AuctionForm();
        form.setTitle("Teak chair");
        form.setDescription("A lounge chair");
        form.setCategory("Antiques");
        form.setStartingPrice(new BigDecimal("600.00"));
        form.setMinIncrement(new BigDecimal("25.00"));
        form.setDurationMinutes(60);
        return form;
    }

    private static AuctionItem upcomingLot(User seller) {
        LotCollection collection = new LotCollection();
        collection.setId(9L);
        collection.setName("Guest bedroom");
        collection.setSeller(seller);
        AuctionItem lot = new AuctionItem();
        lot.setId(10L);
        lot.setTitle("Oak side table");
        lot.setSeller(seller);
        lot.setCollection(collection);
        lot.setStatus(AuctionStatus.SCHEDULED);
        lot.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        lot.setEndTime(Instant.now().plus(3, ChronoUnit.DAYS));
        lot.setStartingPrice(new BigDecimal("220.00"));
        lot.setCurrentPrice(new BigDecimal("220.00"));
        return lot;
    }
}

package com.biddingapp.repository;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, Long> {

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findAllByOrderByEndTimeAsc();

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByStatusOrderByEndTimeAsc(AuctionStatus status);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByStatusOrderByStartTimeAsc(AuctionStatus status);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findBySellerOrderByCreatedAtDesc(User seller);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByWinnerOrderByEndTimeDesc(User winner);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByStatusAndWinnerIsNull(AuctionStatus status);

    @EntityGraph(attributePaths = {"collection", "winner"})
    @Query("""
            SELECT a FROM AuctionItem a
            WHERE a.status = :sold
              AND a.endTime <= :cutoff
              AND NOT EXISTS (
                SELECT p FROM Payment p
                WHERE p.auction = a AND p.status = :succeeded
              )
            """)
    List<AuctionItem> findUnpaidSoldWithEndTimeLessThanEqual(@Param("sold") AuctionStatus sold,
                                                             @Param("succeeded") PaymentStatus succeeded,
                                                             @Param("cutoff") Instant cutoff);

    @EntityGraph(attributePaths = {"collection", "winner"})
    @Query("""
            SELECT a FROM AuctionItem a
            WHERE a.status = :sold
              AND a.winner = :winner
              AND NOT EXISTS (
                SELECT p FROM Payment p
                WHERE p.auction = a AND p.status = :succeeded
              )
            ORDER BY a.endTime DESC
            """)
    List<AuctionItem> findUnpaidWonBy(@Param("winner") User winner,
                                      @Param("sold") AuctionStatus sold,
                                      @Param("succeeded") PaymentStatus succeeded);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByStatusAndSellerIdNotOrderByCurrentPriceAsc(
            AuctionStatus status, Long sellerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AuctionItem> findByStatusAndStartTimeLessThanEqual(AuctionStatus status, Instant now);

    List<AuctionItem> findByStatusAndEndTimeLessThanEqual(AuctionStatus status, Instant now);

    @EntityGraph(attributePaths = {"collection", "winner"})
    @Query("SELECT DISTINCT a FROM AuctionItem a JOIN Bid b ON b.auction = a WHERE b.bidder = :bidder ORDER BY a.endTime ASC")
    List<AuctionItem> findAuctionsBidOnBy(@Param("bidder") User bidder);

    @EntityGraph(attributePaths = {"collection", "winner"})
    @Query("""
            SELECT a FROM AuctionItem a
            WHERE a.status = :sold
              AND a.winner IS NOT NULL
              AND a.winner.id <> :bidderId
              AND EXISTS (SELECT b FROM Bid b WHERE b.auction = a AND b.bidder.id = :bidderId)
            ORDER BY a.endTime DESC
            """)
    List<AuctionItem> findLostByBidder(@Param("bidderId") Long bidderId, @Param("sold") AuctionStatus sold);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByCollection_IdOrderByIdAsc(Long collectionId);

    @EntityGraph(attributePaths = {"collection", "winner"})
    List<AuctionItem> findByCollection_IdInOrderByIdAsc(List<Long> collectionIds);

    @EntityGraph(attributePaths = "seller")
    List<AuctionItem> findByCollectionIsNull();

    @Query("""
            SELECT a.collection.id, COUNT(a)
            FROM AuctionItem a
            WHERE a.collection.id IN :ids
            GROUP BY a.collection.id
            """)
    List<Object[]> countLotsByCollectionIds(@Param("ids") List<Long> ids);

    @Query("""
            SELECT a FROM AuctionItem a
            JOIN FETCH a.seller
            LEFT JOIN FETCH a.winner
            LEFT JOIN FETCH a.collection
            WHERE a.id = :id
            """)
    Optional<AuctionItem> findWithSellerById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AuctionItem a JOIN FETCH a.seller WHERE a.id = :id")
    Optional<AuctionItem> findWithSellerByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE auction_items SET version = 0 WHERE version IS NULL", nativeQuery = true)
    int backfillNullVersions();
}

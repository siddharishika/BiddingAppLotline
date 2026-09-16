package com.biddingapp.repository;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.auction = :auction ORDER BY b.placedAt DESC")
    List<Bid> findByAuctionOrderByPlacedAtDesc(@Param("auction") AuctionItem auction);

    long countByAuction(AuctionItem auction);

    @Query("SELECT COUNT(DISTINCT b.bidder.id) FROM Bid b WHERE b.auction = :auction")
    long countDistinctBiddersByAuction(@Param("auction") AuctionItem auction);

    @Query("SELECT b.auction.id, COUNT(DISTINCT b.bidder.id) FROM Bid b WHERE b.auction.id IN :ids GROUP BY b.auction.id")
    List<Object[]> countDistinctBiddersByAuctionIds(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = "bidder")
    Optional<Bid> findTopByAuctionOrderByAmountDescPlacedAtAsc(AuctionItem auction);

    Optional<Bid> findTopByAuctionAndBidderOrderByPlacedAtDesc(AuctionItem auction, User bidder);

    @Query("""
            SELECT b FROM Bid b JOIN FETCH b.auction
            WHERE b.bidder = :bidder AND b.auction.id IN :ids
            ORDER BY b.placedAt DESC
            """)
    List<Bid> findByBidderAndAuctionIdsOrderByPlacedAtDesc(@Param("bidder") User bidder,
                                                           @Param("ids") List<Long> ids);

    @Query("SELECT b FROM Bid b JOIN FETCH b.auction a JOIN FETCH a.seller WHERE b.bidder = :bidder ORDER BY b.placedAt DESC")
    List<Bid> findByBidderWithAuction(@Param("bidder") User bidder);

    boolean existsByAuctionAndBidder(AuctionItem auction, User bidder);

    void deleteByAuction(AuctionItem auction);
}

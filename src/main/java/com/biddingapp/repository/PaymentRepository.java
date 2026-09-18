package com.biddingapp.repository;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.Payment;
import com.biddingapp.domain.PaymentStatus;
import com.biddingapp.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByAuctionAndStatus(AuctionItem auction, PaymentStatus status);

    @Query("SELECT p FROM Payment p JOIN FETCH p.auction WHERE p.payer = :payer ORDER BY p.createdAt DESC")
    List<Payment> findByPayerOrderByCreatedAtDesc(@Param("payer") User payer);

    boolean existsByAuctionAndStatus(AuctionItem auction, PaymentStatus status);

    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.auction a
            LEFT JOIN FETCH a.winner
            JOIN FETCH p.payer
            WHERE p.checkoutSessionId = :sessionId
            """)
    Optional<Payment> findByCheckoutSessionId(@Param("sessionId") String sessionId);

    List<Payment> findByAuctionAndStatus(AuctionItem auction, PaymentStatus status);

    @Query("SELECT p.auction.id FROM Payment p WHERE p.status = :status AND p.auction.id IN :ids")
    List<Long> findAuctionIdsByStatus(@Param("status") PaymentStatus status, @Param("ids") List<Long> ids);

    void deleteByAuction(AuctionItem auction);
}

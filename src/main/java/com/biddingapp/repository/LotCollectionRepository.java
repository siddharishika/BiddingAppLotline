package com.biddingapp.repository;

import com.biddingapp.domain.LotCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LotCollectionRepository extends JpaRepository<LotCollection, Long> {

    boolean existsByName(String name);

    @Query("SELECT c FROM LotCollection c JOIN FETCH c.seller ORDER BY c.createdAt DESC")
    List<LotCollection> findAllWithSellerOrderByCreatedAtDesc();

    @Query("SELECT c FROM LotCollection c JOIN FETCH c.seller WHERE c.id = :id")
    Optional<LotCollection> findWithSellerById(@Param("id") Long id);
}

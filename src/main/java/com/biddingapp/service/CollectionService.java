package com.biddingapp.service;

import com.biddingapp.config.CatalogueCopy;
import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.LotCollectionRepository;
import com.biddingapp.web.dto.CollectionDetailDto;
import com.biddingapp.web.dto.CollectionSummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CollectionService {

    private final LotCollectionRepository lotCollectionRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final BidService bidService;

    public CollectionService(LotCollectionRepository lotCollectionRepository,
                             AuctionItemRepository auctionItemRepository,
                             BidService bidService) {
        this.lotCollectionRepository = lotCollectionRepository;
        this.auctionItemRepository = auctionItemRepository;
        this.bidService = bidService;
    }

    @Transactional(readOnly = true)
    public List<CollectionSummaryDto> catalogue() {
        List<LotCollection> collections = lotCollectionRepository.findAllWithSellerOrderByCreatedAtDesc();
        Map<Long, List<AuctionItem>> lotsByCollection = lotsByCollection(collections);
        List<CollectionSummaryDto> catalogue = new ArrayList<>();
        for (LotCollection collection : collections) {
            List<AuctionItem> lots = lotsByCollection.getOrDefault(collection.getId(), List.of()).stream()
                    .filter(lot -> lot.getStatus() != AuctionStatus.CANCELLED)
                    .toList();
            if (lots.isEmpty()) {
                continue;
            }
            catalogue.add(CollectionSummaryDto.from(collection, lots));
        }
        return catalogue;
    }

    @Transactional(readOnly = true)
    public List<CollectionSummaryDto> liveCatalogue() {
        return catalogue().stream()
                .filter(collection -> collection.lotCount() >= 2)
                .filter(collection -> collection.liveLotCount() > 0)
                .filter(collection -> AuctionStatus.LIVE.name().equals(collection.status()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionDetailDto require(Long id) {
        LotCollection collection = lotCollectionRepository.findWithSellerById(id)
                .orElseThrow(() -> new NotFoundException("Collection not found"));
        List<AuctionItem> lots = auctionItemRepository.findByCollection_IdOrderByIdAsc(collection.getId()).stream()
                .filter(lot -> lot.getStatus() != AuctionStatus.CANCELLED)
                .toList();
        if (lots.isEmpty()) {
            throw new NotFoundException("Collection not found");
        }
        return CollectionDetailDto.from(collection, bidService.toSummaries(lots), rollStatus(lots));
    }

    @Transactional(readOnly = true)
    public List<CollectionSummaryDto> suggestedLive(User user, int limit) {
        Set<Long> alreadyBidOn = bidOnCollectionIds(user);
        return catalogue().stream()
                .filter(collection -> collection.lotCount() >= 2)
                .filter(collection -> AuctionStatus.LIVE.name().equals(collection.status()))
                .filter(collection -> !user.getUsername().equals(collection.sellerUsername()))
                .filter(collection -> !alreadyBidOn.contains(collection.id()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionSummaryDto> bidOnBy(User user) {
        Map<Long, Integer> bidCounts = bidCountsByCollection(user);
        if (bidCounts.isEmpty()) {
            return List.of();
        }
        return catalogue().stream()
                .filter(collection -> collection.lotCount() >= 2)
                .filter(collection -> bidCounts.containsKey(collection.id()))
                .map(collection -> collection.withMyBidCount(bidCounts.get(collection.id())))
                .toList();
    }

    private Map<Long, Integer> bidCountsByCollection(User user) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (AuctionItem lot : auctionItemRepository.findAuctionsBidOnBy(user)) {
            if (lot.getCollection() == null || lot.getCollection().getId() == null) {
                continue;
            }
            counts.merge(lot.getCollection().getId(), 1, Integer::sum);
        }
        return counts;
    }

    private Set<Long> bidOnCollectionIds(User user) {
        return new HashSet<>(bidCountsByCollection(user).keySet());
    }

    @Transactional
    public int wrapOrphans() {
        List<AuctionItem> orphans = auctionItemRepository.findByCollectionIsNull();
        for (AuctionItem lot : orphans) {
            User seller = lot.getSeller();
            LotCollection collection = new LotCollection();
            collection.setName(lot.getTitle());
            collection.setDescription(lot.getDescription());
            collection.setSeller(seller);
            lot.setCollection(lotCollectionRepository.save(collection));
        }
        if (!orphans.isEmpty()) {
            auctionItemRepository.saveAll(orphans);
        }
        return orphans.size();
    }

    @Transactional
    public int refreshSeededCopy() {
        Map<String, String> lots = CatalogueCopy.lotsByTitle();
        Map<String, String> images = CatalogueCopy.imagesByTitle();
        Map<String, String> collections = CatalogueCopy.collectionsByName();
        int updated = 0;
        for (AuctionItem lot : auctionItemRepository.findAll()) {
            String copy = lots.get(lot.getTitle());
            if (copy != null && !copy.equals(lot.getDescription())) {
                lot.setDescription(copy);
                updated++;
            }
            String image = images.get(lot.getTitle());
            if (image != null && !image.equals(lot.getImageUrl())) {
                lot.setImageUrl(image);
                updated++;
            }
        }
        for (LotCollection collection : lotCollectionRepository.findAll()) {
            String copy = collections.get(collection.getName());
            if (copy == null && (collection.getDescription() == null || collection.getDescription().isBlank())) {
                List<AuctionItem> members = auctionItemRepository.findByCollection_IdOrderByIdAsc(collection.getId());
                if (!members.isEmpty()) {
                    copy = members.get(0).getDescription();
                }
            }
            if (copy != null && !copy.equals(collection.getDescription())) {
                collection.setDescription(copy);
                updated++;
            }
        }
        return updated;
    }

    private Map<Long, List<AuctionItem>> lotsByCollection(List<LotCollection> collections) {
        List<Long> ids = collections.stream().map(LotCollection::getId).toList();
        Map<Long, List<AuctionItem>> grouped = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return grouped;
        }
        for (AuctionItem lot : auctionItemRepository.findByCollection_IdInOrderByIdAsc(ids)) {
            Long collectionId = lot.getCollection() == null ? null : lot.getCollection().getId();
            if (collectionId == null) {
                continue;
            }
            grouped.computeIfAbsent(collectionId, key -> new ArrayList<>()).add(lot);
        }
        return grouped;
    }

    public static String rollStatus(List<AuctionItem> lots) {
        boolean scheduled = false;
        boolean sold = false;
        for (AuctionItem lot : lots) {
            AuctionStatus status = lot.getStatus();
            if (status == AuctionStatus.LIVE) {
                return AuctionStatus.LIVE.name();
            }
            if (status == AuctionStatus.SCHEDULED) {
                scheduled = true;
            }
            if (status == AuctionStatus.SOLD) {
                sold = true;
            }
        }
        if (scheduled) {
            return AuctionStatus.SCHEDULED.name();
        }
        if (sold) {
            return AuctionStatus.SOLD.name();
        }
        if (lots.stream().allMatch(lot -> lot.getStatus() == AuctionStatus.CANCELLED)) {
            return AuctionStatus.CANCELLED.name();
        }
        return AuctionStatus.ENDED.name();
    }

    public static List<String> uniqueCategories(List<AuctionItem> lots) {
        return lots.stream().map(AuctionItem::getCategory).filter(Objects::nonNull).distinct().toList();
    }

    public static String coverImage(List<AuctionItem> lots) {
        return lots.stream().map(AuctionItem::getImageUrl).filter(url -> url != null && !url.isBlank()).findFirst().orElse(null);
    }

    public static List<String> previewImages(List<AuctionItem> lots) {
        return lots.stream()
                .map(AuctionItem::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .limit(4)
                .toList();
    }
}

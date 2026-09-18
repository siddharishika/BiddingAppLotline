package com.biddingapp.config;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.Bid;
import com.biddingapp.domain.LotCollection;
import com.biddingapp.domain.Role;
import com.biddingapp.domain.User;
import com.biddingapp.repository.AuctionItemRepository;
import com.biddingapp.repository.BidRepository;
import com.biddingapp.repository.LotCollectionRepository;
import com.biddingapp.repository.UserRepository;
import com.biddingapp.service.AuctionService;
import com.biddingapp.service.CollectionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    @Order(1)
    CommandLineRunner seedCatalog(UserRepository users,
                                  AuctionItemRepository auctions,
                                  BidRepository bids,
                                  LotCollectionRepository collections,
                                  CollectionService collectionService,
                                  AuctionService auctionService,
                                  PasswordEncoder encoder) {
        return args -> {
            auctionService.backfillOptimisticLocks();
            if (users.count() == 0) {
                User admin = saveUser(users, encoder, "admin", "admin@lotline.local", Role.ADMIN);
                User seller = saveUser(users, encoder, "seller", "seller@lotline.local", Role.USER);
                User mara = saveUser(users, encoder, "mara", "mara@lotline.local", Role.USER);
                User julian = saveUser(users, encoder, "julian", "julian@lotline.local", Role.USER);
                seedSingleLots(auctions, bids, collections, seller, mara, julian);
            }
            collectionService.wrapOrphans();
            collectionService.refreshSeededCopy();
            seedCollectionsIfMissing(users, auctions, bids, collections);
            auctionService.closeExpired(Instant.now());
            // Payment receipts are never seeded. Only real Stripe Checkout rows belong in payments.
        };
    }

    private static void seedSingleLots(AuctionItemRepository auctions,
                                       BidRepository bids,
                                       LotCollectionRepository collections,
                                       User seller,
                                       User mara,
                                       User julian) {
        Instant now = Instant.now();

        AuctionItem watch = auctions.save(lot(
                "1968 Omega Seamaster Chronograph",
                CatalogueCopy.WATCH,
                "Watches",
                "https://images.unsplash.com/photo-1523170335258-f5ed97fb696a?auto=format&fit=crop&w=1400&q=80",
                "4200.00", "100.00", seller, now.minus(Duration.ofHours(6)), now.minus(Duration.ofHours(2)),
                AuctionStatus.LIVE, collections.save(namedCollection("1968 Omega Seamaster Chronograph", CatalogueCopy.WATCH, seller))));
        AuctionItem book = auctions.save(lot(
                "First edition of One Hundred Years of Solitude",
                CatalogueCopy.BOOK,
                "Books",
                "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=1400&q=80",
                "850.00", "50.00", seller, now.minus(Duration.ofHours(5)), now.minus(Duration.ofHours(1)),
                AuctionStatus.LIVE, collections.save(namedCollection("First edition of One Hundred Years of Solitude", CatalogueCopy.BOOK, seller))));
        AuctionItem painting = auctions.save(lot(
                "Coastal study in oil, unsigned c. 1920",
                CatalogueCopy.PAINTING,
                "Art",
                "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?auto=format&fit=crop&w=1400&q=80",
                "1500.00", "75.00", seller, now.minus(Duration.ofHours(4)), now.minus(Duration.ofMinutes(40)),
                AuctionStatus.LIVE, collections.save(namedCollection("Coastal study in oil, unsigned c. 1920", CatalogueCopy.PAINTING, seller))));
        AuctionItem camera = auctions.save(lot(
                "Leica M3 body, 1955",
                CatalogueCopy.CAMERA,
                "Electronics",
                "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=1400&q=80",
                "2100.00", "100.00", mara, now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(95)),
                AuctionStatus.LIVE, collections.save(namedCollection("Leica M3 body, 1955", CatalogueCopy.CAMERA, mara))));
        AuctionItem vinyl = auctions.save(lot(
                "Kind of Blue — original six-eye Columbia pressing",
                CatalogueCopy.VINYL,
                "Collectibles",
                "https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?auto=format&fit=crop&w=1400&q=80",
                "320.00", "20.00", julian, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(52)),
                AuctionStatus.LIVE, collections.save(namedCollection("Kind of Blue — original six-eye Columbia pressing", CatalogueCopy.VINYL, julian))));
        auctions.save(lot(
                "Danish teak lounge chair, 1960s",
                CatalogueCopy.CHAIR,
                "Antiques",
                "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=1400&q=80",
                "600.00", "25.00", seller, now.plus(Duration.ofMinutes(15)), now.plus(Duration.ofHours(4)),
                AuctionStatus.SCHEDULED, collections.save(namedCollection("Danish teak lounge chair, 1960s", CatalogueCopy.CHAIR, seller))));

        place(bids, watch, mara, "4300.00", now.minus(Duration.ofHours(5)));
        place(bids, watch, julian, "4500.00", now.minus(Duration.ofHours(4)));
        place(bids, watch, mara, "4700.00", now.minus(Duration.ofHours(3)));
        watch = reload(auctions, watch.getId());
        watch.setCurrentPrice(new BigDecimal("4700.00"));
        watch.setWinner(mara);
        watch.setStatus(AuctionStatus.SOLD);
        watch = auctions.save(watch);

        place(bids, book, julian, "900.00", now.minus(Duration.ofHours(4)));
        place(bids, book, mara, "980.00", now.minus(Duration.ofHours(2)));
        book = reload(auctions, book.getId());
        book.setCurrentPrice(new BigDecimal("980.00"));
        book.setWinner(mara);
        book.setStatus(AuctionStatus.SOLD);
        book = auctions.save(book);

        place(bids, painting, julian, "1650.00", now.minus(Duration.ofHours(1)));
        painting = reload(auctions, painting.getId());
        painting.setCurrentPrice(new BigDecimal("1650.00"));
        painting.setWinner(julian);
        painting.setStatus(AuctionStatus.SOLD);
        painting = auctions.save(painting);

        place(bids, vinyl, seller, "360.00", now.minus(Duration.ofMinutes(3)));
        vinyl = reload(auctions, vinyl.getId());
        vinyl.setCurrentPrice(new BigDecimal("360.00"));
        auctions.save(vinyl);
    }

    private static void seedCollectionsIfMissing(UserRepository users,
                                                 AuctionItemRepository auctions,
                                                 BidRepository bids,
                                                 LotCollectionRepository collections) {
        User seller = users.findByUsername("seller").orElse(null);
        User mara = users.findByUsername("mara").orElse(null);
        User julian = users.findByUsername("julian").orElse(null);
        if (seller == null || mara == null || julian == null) {
            return;
        }

        Instant now = Instant.now();

        if (!collections.existsByName("A weekend by the sea")) {
            LotCollection weekend = collections.save(namedCollection("A weekend by the sea", CatalogueCopy.WEEKEND_BY_THE_SEA, seller));
            AuctionItem deckChair = auctions.save(lot(
                    "Canvas deck chair, striped",
                    CatalogueCopy.DECK_CHAIR,
                    "Antiques",
                    "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1400&q=80",
                    "180.00", "15.00", seller, now.minus(Duration.ofMinutes(25)), now.plus(Duration.ofMinutes(70)),
                    AuctionStatus.LIVE, weekend));
            AuctionItem hamper = auctions.save(lot(
                    "Enamel picnic hamper",
                    CatalogueCopy.HAMPER,
                    "Collectibles",
                    "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?auto=format&fit=crop&w=1400&q=80",
                    "90.00", "10.00", seller, now.minus(Duration.ofMinutes(25)), now.plus(Duration.ofMinutes(70)),
                    AuctionStatus.LIVE, weekend));
            AuctionItem tideChart = auctions.save(lot(
                    "Tide chart in a gilt frame",
                    CatalogueCopy.TIDE_CHART,
                    "Art",
                    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1400&q=80",
                    "240.00", "20.00", seller, now.minus(Duration.ofMinutes(25)), now.plus(Duration.ofMinutes(70)),
                    AuctionStatus.LIVE, weekend));

            place(bids, deckChair, mara, "195.00", now.minus(Duration.ofMinutes(18)));
            place(bids, deckChair, julian, "210.00", now.minus(Duration.ofMinutes(9)));
            deckChair.setCurrentPrice(new BigDecimal("210.00"));

            place(bids, hamper, mara, "110.00", now.minus(Duration.ofMinutes(12)));
            hamper.setCurrentPrice(new BigDecimal("110.00"));

            place(bids, tideChart, julian, "260.00", now.minus(Duration.ofMinutes(14)));
            place(bids, tideChart, mara, "280.00", now.minus(Duration.ofMinutes(4)));
            tideChart.setCurrentPrice(new BigDecimal("280.00"));

            auctions.saveAll(List.of(deckChair, hamper, tideChart));
        }

        if (!collections.existsByName("After midnight at the club")) {
            LotCollection club = collections.save(namedCollection("After midnight at the club", CatalogueCopy.AFTER_MIDNIGHT, julian));
            AuctionItem shaker = auctions.save(lot(
                    "Silver cocktail shaker, 1930s",
                    CatalogueCopy.SHAKER,
                    "Antiques",
                    "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=1400&q=80",
                    "320.00", "20.00", julian, now.minus(Duration.ofMinutes(12)), now.plus(Duration.ofMinutes(95)),
                    AuctionStatus.LIVE, club));
            AuctionItem photograph = auctions.save(lot(
                    "Nightclub photograph, signed",
                    CatalogueCopy.PHOTOGRAPH,
                    "Art",
                    "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1400&q=80",
                    "150.00", "15.00", julian, now.minus(Duration.ofMinutes(12)), now.plus(Duration.ofMinutes(95)),
                    AuctionStatus.LIVE, club));

            place(bids, shaker, mara, "340.00", now.minus(Duration.ofMinutes(8)));
            place(bids, shaker, seller, "360.00", now.minus(Duration.ofMinutes(3)));
            shaker.setCurrentPrice(new BigDecimal("360.00"));

            place(bids, photograph, mara, "180.00", now.minus(Duration.ofMinutes(6)));
            photograph.setCurrentPrice(new BigDecimal("180.00"));

            auctions.saveAll(List.of(shaker, photograph));
        }

        if (!collections.existsByName("The quiet study")) {
            LotCollection study = collections.save(namedCollection("The quiet study", CatalogueCopy.QUIET_STUDY, mara));
            // Keep upcoming long enough that consignors can still withdraw after a demo restart.
            Instant opens = now.plus(Duration.ofDays(2));
            Instant closes = opens.plus(Duration.ofHours(4));
            AuctionItem lamp = lot(
                    "Brass reading lamp",
                    CatalogueCopy.LAMP,
                    "Antiques",
                    "https://images.unsplash.com/photo-1507473885765-e6ed357f3443?auto=format&fit=crop&w=1400&q=80",
                    "220.00", "20.00", mara, opens, closes,
                    AuctionStatus.SCHEDULED, study);
            AuctionItem diaries = lot(
                    "Set of morocco diaries, 1920s",
                    CatalogueCopy.DIARIES,
                    "Books",
                    "https://images.unsplash.com/photo-1519682337058-a94d519337bc?auto=format&fit=crop&w=1400&q=80",
                    "140.00", "10.00", mara, opens, closes,
                    AuctionStatus.SCHEDULED, study);
            auctions.saveAll(List.of(lamp, diaries));
        }

        if (!collections.existsByName("On the kitchen table")) {
            LotCollection kitchen = collections.save(namedCollection(
                    "On the kitchen table",
                    CatalogueCopy.KITCHEN_TABLE,
                    seller));
            AuctionItem pan = lot(
                    "Copper sauté pan",
                    "A tin-lined copper sauté pan with a riveted iron handle. The lining is sound; the exterior has the dark of a stove, not a polish.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1556910103-1c02745aae4d?auto=format&fit=crop&w=1400&q=80",
                    "160.00", "15.00", seller, now.minus(Duration.ofMinutes(8)), now.plus(Duration.ofMinutes(80)),
                    AuctionStatus.LIVE, kitchen);
            AuctionItem jug = lot(
                    "Stoneware cream jug",
                    "A small English stoneware jug, salt-glazed, with a hairline at the lip that does not leak.",
                    "Collectibles",
                    "https://images.unsplash.com/photo-1578500494198-246f612d3b3d?auto=format&fit=crop&w=1400&q=80",
                    "70.00", "10.00", seller, now.minus(Duration.ofMinutes(8)), now.plus(Duration.ofMinutes(80)),
                    AuctionStatus.LIVE, kitchen);
            auctions.saveAll(List.of(pan, jug));
        }

        if (!auctions.findAll().stream().anyMatch(item -> "Tin porch lantern".equals(item.getTitle()))) {
            LotCollection porch = collections.findAll().stream()
                    .filter(collection -> "Under the porch light".equals(collection.getName()))
                    .findFirst()
                    .orElseGet(() -> collections.save(namedCollection(
                            "Under the porch light",
                            CatalogueCopy.PORCH_LIGHT,
                            seller)));
            Instant opened = now.minus(Duration.ofMinutes(15));
            Instant closes = now.plus(Duration.ofHours(4));
            AuctionItem lantern = auctions.save(lot(
                    "Tin porch lantern",
                    "A punched-tin lantern with a clear chimney and a bail handle. The candle plate is sound; the patina is porch weather, not polish.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1513506003901-1e6a229e2d15?auto=format&fit=crop&w=1400&q=80",
                    "85.00", "10.00", seller, opened, closes,
                    AuctionStatus.LIVE, porch));
            AuctionItem foldingTable = auctions.save(lot(
                    "Folding garden table",
                    "A small folding table in painted wood, the top still flat, the hinges a little stiff. Meant for tea outdoors, not for a shop window.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1533090161767-e6ffed986c88?auto=format&fit=crop&w=1400&q=80",
                    "120.00", "15.00", seller, opened, closes,
                    AuctionStatus.LIVE, porch));
            AuctionItem wovenSeat = auctions.save(lot(
                    "Rush-seat stool",
                    "A rush-seat stool with turned legs, the weave tight and the frame unmarked. Sit on it; that is why it was made.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1503602642458-232111445657?auto=format&fit=crop&w=1400&q=80",
                    "95.00", "10.00", seller, opened, closes,
                    AuctionStatus.LIVE, porch));
            place(bids, lantern, mara, "95.00", now.minus(Duration.ofMinutes(8)));
            lantern.setCurrentPrice(new BigDecimal("95.00"));
            place(bids, foldingTable, julian, "135.00", now.minus(Duration.ofMinutes(5)));
            foldingTable.setCurrentPrice(new BigDecimal("135.00"));
            auctions.saveAll(List.of(lantern, foldingTable, wovenSeat));
        }

        if (!collections.existsByName("The one that got away")) {
            LotCollection lost = collections.save(namedCollection(
                    "The one that got away",
                    "A lot mara bid on and julian took at the hammer, so the losing paddle has somewhere to look.",
                    seller));
            Instant opened = now.minus(Duration.ofHours(3));
            Instant closed = now.minus(Duration.ofMinutes(20));
            AuctionItem print = auctions.save(lot(
                    "Travel poster, Cote d'Azur",
                    "A mid-century travel poster for the Cote d'Azur, linen-backed, colours still loud. Fold marks are honest. It hung in a stair and was bid on by two paddles; only one took it home.",
                    "Art",
                    "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1400&q=80",
                    "300.00", "25.00", seller, opened, closed,
                    AuctionStatus.SOLD, lost));
            print.setWinner(julian);
            print.setCurrentPrice(new BigDecimal("375.00"));
            print = auctions.save(print);
            place(bids, print, mara, "350.00", now.minus(Duration.ofMinutes(50)));
            place(bids, print, julian, "375.00", now.minus(Duration.ofMinutes(35)));
        }

        if (!collections.existsByName("Guest bedroom, not yet shown")) {
            LotCollection guest = collections.save(namedCollection(
                    "Guest bedroom, not yet shown",
                    CatalogueCopy.GUEST_BEDROOM,
                    mara));
            Instant opens = now.plus(Duration.ofDays(2));
            Instant closes = opens.plus(Duration.ofHours(6));
            AuctionItem quilt = lot(
                    "Patchwork quilt, 1930s",
                    "A cotton patchwork quilt, 1930s, washed soft. The colours have settled rather than faded. Offered for a bed that is actually slept in.",
                    "Collectibles",
                    "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1400&q=80",
                    "180.00", "15.00", mara, opens, closes,
                    AuctionStatus.SCHEDULED, guest);
            AuctionItem ewer = lot(
                    "Ironstone ewer and basin",
                    "A white ironstone ewer and basin, hairline under the glaze, no chips at the lip. A washstand set, not a display.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1578500494198-246f612d3b3d?auto=format&fit=crop&w=1400&q=80",
                    "90.00", "10.00", mara, opens, closes,
                    AuctionStatus.SCHEDULED, guest);
            auctions.saveAll(List.of(quilt, ewer));
        }

        if (!auctions.findAll().stream().anyMatch(item -> "Oak side table, 1940s".equals(item.getTitle()))) {
            LotCollection table = collections.save(namedCollection(
                    "Oak side table, 1940s",
                    "A small oak side table, 1940s, drawer still running true. Catalogued and not yet open.",
                    mara));
            Instant opens = now.plus(Duration.ofDays(3));
            AuctionItem sideTable = lot(
                    "Oak side table, 1940s",
                    "A small oak side table, 1940s, drawer still running true. Catalogued and not yet open.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=1400&q=80",
                    "220.00", "20.00", mara, opens, opens.plus(Duration.ofHours(4)),
                    AuctionStatus.SCHEDULED, table);
            auctions.save(sideTable);
        }

        if (!auctions.findAll().stream().anyMatch(item -> "Folding camp stool".equals(item.getTitle()))) {
            LotCollection stool = collections.save(namedCollection(
                    "Folding camp stool",
                    "A lot that closed with no bids, so the issuer can withdraw it from the floor.",
                    mara));
            Instant opened = now.minus(Duration.ofHours(4));
            Instant closed = now.minus(Duration.ofMinutes(15));
            AuctionItem campStool = lot(
                    "Folding camp stool",
                    "A canvas camp stool, the frame still sound, the seat faded from a season outdoors. It closed without a paddle.",
                    "Antiques",
                    "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1400&q=80",
                    "40.00", "5.00", mara, opened, closed,
                    AuctionStatus.ENDED, stool);
            auctions.save(campStool);
        }

        if (!auctions.findAll().stream().anyMatch(item -> "Linen valance, unused".equals(item.getTitle()))) {
            LotCollection valance = collections.save(namedCollection(
                    "Linen valance, unused",
                    "A lot mara catalogued and then withdrew before the room opened.",
                    mara));
            Instant opens = now.plus(Duration.ofDays(4));
            AuctionItem linen = lot(
                    "Linen valance, unused",
                    "A pair of unused linen valances, still creased from the packet. Withdrawn from the floor before bidding opened.",
                    "Collectibles",
                    "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1400&q=80",
                    "55.00", "5.00", mara, opens, opens.plus(Duration.ofHours(3)),
                    AuctionStatus.CANCELLED, valance);
            auctions.save(linen);
        }
    }

    private static User saveUser(UserRepository users, PasswordEncoder encoder,
                                 String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode("password123"));
        user.setRole(role);
        user.setEnabled(true);
        return users.save(user);
    }

    private static LotCollection namedCollection(String name, String description, User seller) {
        LotCollection collection = new LotCollection();
        collection.setName(name);
        collection.setDescription(description);
        collection.setSeller(seller);
        return collection;
    }

    private static AuctionItem lot(String title, String description, String category, String imageUrl,
                                   String start, String increment, User seller,
                                   Instant startTime, Instant endTime, AuctionStatus status,
                                   LotCollection collection) {
        AuctionItem item = new AuctionItem();
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setImageUrl(imageUrl);
        item.setStartingPrice(new BigDecimal(start));
        item.setMinIncrement(new BigDecimal(increment));
        item.setCurrentPrice(new BigDecimal(start));
        item.setSeller(seller);
        item.setStartTime(startTime);
        item.setEndTime(endTime);
        item.setStatus(status);
        item.setCollection(collection);
        return item;
    }

    private static AuctionItem reload(AuctionItemRepository auctions, Long id) {
        return auctions.findById(id)
                .orElseThrow(() -> new IllegalStateException("Seed lot missing after insert: id=" + id));
    }

    private static void place(BidRepository bids, AuctionItem auction, User bidder, String amount, Instant when) {
        if (auction.getId() == null) {
            throw new IllegalStateException("Save the lot before placing a seed bid: " + auction.getTitle());
        }
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setAmount(new BigDecimal(amount));
        bid.setPlacedAt(when);
        bids.save(bid);
    }
}

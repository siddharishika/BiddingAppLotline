package com.biddingapp.websocket;

import com.biddingapp.domain.AuctionItem;
import com.biddingapp.domain.Bid;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuctionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public AuctionEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishBid(AuctionItem auction, Bid latest, List<Bid> history) {
        BidUpdateMessage message = new BidUpdateMessage();
        message.setAuctionId(auction.getId());
        message.setCurrentPrice(auction.getCurrentPrice());
        message.setBidCount(history.stream()
                .map(bid -> bid.getBidder().getId())
                .distinct()
                .count());
        message.setPlacedAt(latest.getPlacedAt());
        message.setStatus(auction.getStatus().name());
        message.setMinIncrement(auction.getMinIncrement());
        message.setNextMinimum(auction.getCurrentPrice().add(auction.getMinIncrement()));
        messagingTemplate.convertAndSend("/topic/auctions/" + auction.getId(), message);
    }

    public void publishStatus(AuctionItem auction, long bidCount) {
        BidUpdateMessage message = new BidUpdateMessage();
        message.setAuctionId(auction.getId());
        message.setCurrentPrice(auction.getCurrentPrice());
        message.setBidCount(bidCount);
        message.setStatus(auction.getStatus().name());
        if (auction.getWinner() != null) {
            message.setBidderUsername(auction.getWinner().getUsername());
        }
        try {
            messagingTemplate.convertAndSend("/topic/auctions/" + auction.getId(), message);
            messagingTemplate.convertAndSend("/topic/auctions", message);
        } catch (RuntimeException ignored) {
            // A closed hammer still stands if the socket cannot be notified.
        }
    }
}

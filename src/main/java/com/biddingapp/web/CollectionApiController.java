package com.biddingapp.web;

import com.biddingapp.domain.AuctionStatus;
import com.biddingapp.domain.User;
import com.biddingapp.service.AuctionService;
import com.biddingapp.service.CollectionService;
import com.biddingapp.service.UserService;
import com.biddingapp.web.dto.CollectionDetailDto;
import com.biddingapp.web.dto.CollectionSummaryDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class CollectionApiController {

    private final CollectionService collectionService;
    private final AuctionService auctionService;
    private final UserService userService;

    public CollectionApiController(CollectionService collectionService,
                                   AuctionService auctionService,
                                   UserService userService) {
        this.collectionService = collectionService;
        this.auctionService = auctionService;
        this.userService = userService;
    }

    @GetMapping
    public List<CollectionSummaryDto> list(@RequestParam(required = false) AuctionStatus status) {
        if (status == AuctionStatus.LIVE) {
            return collectionService.liveCatalogue();
        }
        return collectionService.catalogue();
    }

    @GetMapping("/{id:\\d+}")
    public CollectionDetailDto detail(@PathVariable Long id) {
        return collectionService.require(id);
    }

    @PostMapping("/{id:\\d+}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.withdrawCollection(id, issuer);
    }

    @PostMapping("/{id:\\d+}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.reopenCollection(id, issuer);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        User issuer = AuthSupport.currentUser(authentication, userService);
        auctionService.deleteCollection(id, issuer);
    }
}

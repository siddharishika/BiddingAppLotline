package com.biddingapp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogueIsPublic() throws Exception {
        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(content().string(not(containsString("@lotline.local"))));
    }

    @Test
    void collectionCatalogueIsPublic() throws Exception {
        mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].lotCount").exists())
                .andExpect(content().string(not(containsString("@lotline.local"))));
    }

    @Test
    void lotDetailIsPublicAndHidesEmails() throws Exception {
        mockMvc.perform(get("/api/auctions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").exists())
                .andExpect(jsonPath("$.bidderCount").exists())
                .andExpect(jsonPath("$.history").doesNotExist())
                .andExpect(jsonPath("$.sellerUsername").exists())
                .andExpect(content().string(not(containsString("@lotline.local"))));
    }

    @Test
    void listingALotRequiresSignIn() throws Exception {
        mockMvc.perform(post("/api/auctions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test lot","description":"A listed object","category":"Art","startingPrice":100,"minIncrement":10,"durationMinutes":30}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void biddingRequiresSignIn() throws Exception {
        mockMvc.perform(post("/api/auctions/1/bids")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "mara")
    void signedInUserCanSeeLiveLotBidState() throws Exception {
        mockMvc.perform(get("/api/auctions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seller").value(false))
                .andExpect(jsonPath("$.minBid").exists())
                .andExpect(jsonPath("$.myLastBidAmount").value(4700.0));
    }
}

package com.crawler.controller;

import com.crawler.model.ContentPage;
import com.crawler.model.DiscoveryHit;
import com.crawler.service.ContentDiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiscoveryController.class)
public class DiscoveryControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ContentDiscoveryService discoveryService;

    @Test
    void discoverReturnsHits() throws Exception {
        when(discoveryService.discover(eq("https://s.test"), eq("crawler"), eq(1))).thenReturn(List.of(
                new DiscoveryHit("https://s.test/a", "A", "…crawler…", 2, 1)));

        mvc.perform(post("/api/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://s.test\",\"keyword\":\"crawler\",\"depth\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://s.test/a"))
                .andExpect(jsonPath("$[0].matchCount").value(2));
    }

    @Test
    void discoverBadUrlIs400() throws Exception {
        when(discoveryService.discover(eq("nope"), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid URL: nope"));

        mvc.perform(post("/api/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"nope\",\"keyword\":\"crawler\",\"depth\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contentReturnsFullPage() throws Exception {
        when(discoveryService.fetchContent("https://s.test/a"))
                .thenReturn(new ContentPage("https://s.test/a", "A", "full body text here"));

        mvc.perform(get("/api/discover/content").param("url", "https://s.test/a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A"))
                .andExpect(jsonPath("$.text").value("full body text here"));
    }

    @Test
    void contentBadUrlIs400() throws Exception {
        when(discoveryService.fetchContent("nope"))
                .thenThrow(new IllegalArgumentException("Invalid URL: nope"));

        mvc.perform(get("/api/discover/content").param("url", "nope"))
                .andExpect(status().isBadRequest());
    }
}

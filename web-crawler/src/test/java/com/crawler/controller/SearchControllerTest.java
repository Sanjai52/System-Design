package com.crawler.controller;

import com.crawler.model.SearchHit;
import com.crawler.service.RankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
public class SearchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RankService rankService;

    @Test
    void searchReturnsRankedHits() throws Exception {
        when(rankService.search(eq("java"), eq(10))).thenReturn(List.of(
                new SearchHit("https://d.test", "Doc", "snippet here", 1.5)));

        mvc.perform(get("/api/search").param("q", "java").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://d.test"))
                .andExpect(jsonPath("$[0].score").value(1.5));
    }

    @Test
    void searchBlankIs400() throws Exception {
        when(rankService.search(eq(" "), eq(10))).thenThrow(new IllegalArgumentException("blank"));

        mvc.perform(get("/api/search").param("q", " ").param("limit", "10"))
                .andExpect(status().isBadRequest());
    }
}

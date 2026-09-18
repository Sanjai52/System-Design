package com.crawler.controller;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.HierarchyNode;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.CrawlService;
import com.crawler.service.HierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlController.class)
public class CrawlControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CrawlService crawlService;

    @MockBean
    CrawlStateRepository crawlStateRepository;

    @MockBean
    HierarchyService hierarchyService;

    @Test
    void hierarchyReturnsTree() throws Exception {
        HierarchyNode root = new HierarchyNode("https://s.test", CrawlStatus.COMPLETED, 0, 1);
        root.getChildren().add(new HierarchyNode("https://s.test/a", CrawlStatus.COMPLETED, 1, 0));
        when(hierarchyService.buildTree(eq("j1"), any())).thenReturn(root);

        mvc.perform(get("/api/crawl/j1/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("j1"))
                .andExpect(jsonPath("$.tree.url").value("https://s.test"))
                .andExpect(jsonPath("$.tree.children[0].url").value("https://s.test/a"));
    }

    @Test
    void hierarchyUnknownJobIs404() throws Exception {
        when(hierarchyService.buildTree(eq("nope"), any())).thenThrow(new NoSuchElementException("gone"));

        mvc.perform(get("/api/crawl/nope/hierarchy"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchReturnsMatches() throws Exception {
        CrawlJob job = new CrawlJob("j1", "https://example.com", 10);
        job.setStatus(CrawlStatus.COMPLETED);
        when(hierarchyService.searchBySeed("example")).thenReturn(List.of(job));

        mvc.perform(get("/api/crawl/search").param("seedUrl", "example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("j1"));
    }

    @Test
    void searchBlankIs400() throws Exception {
        when(hierarchyService.searchBySeed(" ")).thenThrow(new IllegalArgumentException("blank"));

        mvc.perform(get("/api/crawl/search").param("seedUrl", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void latestReturnsNewestJob() throws Exception {
        CrawlJob job = new CrawlJob("j-new", "https://new.test", 10);
        job.setStatus(CrawlStatus.CRAWLING);
        when(hierarchyService.getLatestJob()).thenReturn(java.util.Optional.of(job));

        mvc.perform(get("/api/crawl/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("j-new"));
    }

    @Test
    void latestWithNoJobsIs404() throws Exception {
        when(hierarchyService.getLatestJob()).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/api/crawl/latest"))
                .andExpect(status().isNotFound());
    }
}

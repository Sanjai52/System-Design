package com.crawler.service;

import com.crawler.model.SearchHit;
import com.crawler.repository.CrawlStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RankServiceTest {

    @Mock
    CrawlStateRepository repo;

    @InjectMocks
    RankService service;

    @Test
    void higherTfRanksFirstWhenIdfZero() {
        when(repo.getTotalDocs()).thenReturn(2L);
        when(repo.getTermScores("example")).thenReturn(Map.of(
                "https://a.test", 3.0, "https://b.test", 1.0));
        when(repo.getTermDocCount("example")).thenReturn(2L);
        when(repo.getDoc("https://a.test")).thenReturn(Map.of("title", "A", "snippet", "s", "length", "10"));
        when(repo.getDoc("https://b.test")).thenReturn(Map.of("title", "B", "snippet", "s", "length", "10"));

        List<SearchHit> hits = service.search("example", 10);

        assertEquals(2, hits.size());
        assertEquals("https://a.test", hits.get(0).getUrl());
    }

    @Test
    void rareTermOutranksCommonTerm() {
        when(repo.getTotalDocs()).thenReturn(10L);
        when(repo.getTermScores("common")).thenReturn(Map.of("https://a.test", 5.0));
        when(repo.getTermScores("rare")).thenReturn(Map.of("https://b.test", 1.0));
        when(repo.getTermDocCount("common")).thenReturn(9L);
        when(repo.getTermDocCount("rare")).thenReturn(1L);
        when(repo.getDoc("https://a.test")).thenReturn(Map.of("title", "A", "snippet", "s", "length", "10"));
        when(repo.getDoc("https://b.test")).thenReturn(Map.of("title", "B", "snippet", "s", "length", "10"));

        List<SearchHit> hits = service.search("common rare", 10);

        assertEquals("https://b.test", hits.get(0).getUrl());
    }

    @Test
    void unknownTermYieldsEmpty() {
        when(repo.getTotalDocs()).thenReturn(5L);
        when(repo.getTermScores("xyzzy")).thenReturn(Map.of());

        List<SearchHit> hits = service.search("xyzzy", 10);

        assertTrue(hits.isEmpty());
    }

    @Test
    void blankQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.search("  ", 10));
    }
}

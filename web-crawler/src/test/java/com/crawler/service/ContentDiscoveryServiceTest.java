package com.crawler.service;

import com.crawler.model.DiscoveryHit;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ContentDiscoveryServiceTest {

    @Mock
    PageFetcherService fetcher;

    UrlDiscoveryService discovery = new UrlDiscoveryService();

    ContentDiscoveryService service() {
        return new ContentDiscoveryService(fetcher, discovery);
    }

    private static String page(String title, String body) {
        return "<html><head><title>" + title + "</title></head><body>" + body + "</body></html>";
    }

    @Test
    void findsKeywordInSeedAndDirectLinks() throws Exception {
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                page("Seed", "nothing here <a href=\"https://s.test/a\">a</a> <a href=\"https://s.test/b\">b</a>"),
                "https://s.test"));
        when(fetcher.fetchPage("https://s.test/a")).thenReturn(Jsoup.parse(
                page("A", "the crawler visits pages"), "https://s.test/a"));
        when(fetcher.fetchPage("https://s.test/b")).thenReturn(Jsoup.parse(
                page("B", "no match here"), "https://s.test/b"));

        List<DiscoveryHit> hits = service().discover("https://s.test", "crawler", 1);

        assertEquals(1, hits.size());
        assertEquals("https://s.test/a", hits.get(0).getUrl());
        assertTrue(hits.get(0).getMatchCount() >= 1);
        assertTrue(hits.get(0).getSnippet().toLowerCase().contains("crawler"));
    }

    @Test
    void respectsDepthLimit() throws Exception {
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                page("Seed", "<a href=\"https://s.test/a\">a</a>"), "https://s.test"));
        when(fetcher.fetchPage("https://s.test/a")).thenReturn(Jsoup.parse(
                page("A", "crawler here <a href=\"https://s.test/deep\">deep</a>"), "https://s.test/a"));

        List<DiscoveryHit> depth1 = service().discover("https://s.test", "crawler", 1);

        assertEquals(1, depth1.size());
        assertEquals("https://s.test/a", depth1.get(0).getUrl());
    }

    @Test
    void fetchFailureDoesNotAbort() throws Exception {
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                page("Seed", "<a href=\"https://s.test/a\">a</a> <a href=\"https://s.test/b\">b</a>"),
                "https://s.test"));
        when(fetcher.fetchPage("https://s.test/a")).thenThrow(new IOException("boom"));
        when(fetcher.fetchPage("https://s.test/b")).thenReturn(Jsoup.parse(
                page("B", "crawler found"), "https://s.test/b"));

        List<DiscoveryHit> hits = service().discover("https://s.test", "crawler", 1);

        assertEquals(1, hits.size());
        assertEquals("https://s.test/b", hits.get(0).getUrl());
    }

    @Test
    void blankKeywordAndBadUrlRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service().discover("https://s.test", "  ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service().discover("not-a-url", "crawler", 1));
    }
}

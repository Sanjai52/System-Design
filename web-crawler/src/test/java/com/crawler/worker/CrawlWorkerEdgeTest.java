package com.crawler.worker;

import com.crawler.model.CrawlStatus;
import com.crawler.model.CrawlTask;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.QueueService;
import com.crawler.service.UrlDiscoveryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CrawlWorkerEdgeTest {

    @Mock
    PageFetcherService fetcher;

    @Mock
    CrawlStateRepository repo;

    UrlDiscoveryService discovery = new UrlDiscoveryService();
    QueueService queues = new QueueService();

    @Test
    void singleWorkerCrawlsOnePageAndFinishes() throws Exception {
        when(repo.markVisited(any(), any())).thenReturn(true);
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                "<html><head><title>T</title></head><body><a href=\"https://s.test/a\">a</a></body></html>",
                "https://s.test"));
        queues.createQueue("j1");
        CrawlJobContext ctx = new CrawlJobContext("j1", 1, 1, queues);
        ctx.addTask(new CrawlTask("https://s.test", null, 0));
        AtomicBoolean hookRan = new AtomicBoolean(false);

        new CrawlWorker("j1", ctx, fetcher, discovery, repo, () -> hookRan.set(true)).run();

        assertEquals(1, ctx.getPages());
        assertTrue(hookRan.get());
        verify(repo).completeJob("j1");
        verify(repo).saveUrlResult(eq("j1"), argThat(r ->
                r.getStatus() == CrawlStatus.COMPLETED
                        && r.getChildUrls().equals(List.of("https://s.test/a"))));
    }
}

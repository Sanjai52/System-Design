package com.crawler.worker;

import com.crawler.model.CrawlTask;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.QueueService;
import com.crawler.service.UrlDiscoveryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkerIndexTest {

    @Mock
    PageFetcherService fetcher;

    @Mock
    CrawlStateRepository repo;

    UrlDiscoveryService discovery = new UrlDiscoveryService();
    QueueService queues = new QueueService();

    @Test
    @SuppressWarnings("unchecked")
    void successfulFetchIndexesPageText() throws Exception {
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                "<html><head><title>Test Page</title></head><body>Test crawler test page crawler</body></html>",
                "https://s.test"));
        queues.createQueue("j1");
        CrawlJobContext ctx = new CrawlJobContext("j1", 1, 1, queues);
        ctx.addTask(new CrawlTask("https://s.test", null, 0));

        new CrawlWorker("j1", ctx, fetcher, discovery, repo, () -> {}).run();

        ArgumentCaptor<Map<String, Integer>> tfCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repo).indexPage(eq("https://s.test"), eq("j1"), eq("Test Page"),
                anyString(), anyInt(), tfCaptor.capture());
        assertEquals(2, tfCaptor.getValue().get("crawler"));
        assertEquals(2, tfCaptor.getValue().get("test"));
    }
}

package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueueTaskTest {
    @Test
    void queueRoundTripsParentAndDepth() {
        QueueService q = new QueueService();
        q.clear();
        q.addTask(new CrawlTask("https://example.com/a", "https://example.com", 1));
        CrawlTask got = q.pollTask();
        assertNotNull(got);
        assertEquals("https://example.com/a", got.getUrl());
        assertEquals("https://example.com", got.getParentUrl());
        assertEquals(1, got.getDepth());
    }
}

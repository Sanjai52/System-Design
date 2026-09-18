package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueueTaskTest {

    @Test
    void perJobQueuesNeverCross() throws Exception {
        QueueService q = new QueueService();
        q.createQueue("j1");
        q.createQueue("j2");
        q.addTask("j1", new CrawlTask("https://a.test", null, 0));
        q.addTask("j2", new CrawlTask("https://b.test", null, 0));

        assertEquals("https://a.test", q.pollTask("j1", 100).getUrl());
        assertEquals("https://b.test", q.pollTask("j2", 100).getUrl());
        assertNull(q.pollTask("j1", 50));
    }

    @Test
    void removedQueueRejectsTasks() {
        QueueService q = new QueueService();
        q.createQueue("j1");
        q.removeQueue("j1");

        assertEquals(0, q.queueSize("j1"));
        assertThrows(IllegalStateException.class,
                () -> q.addTask("j1", new CrawlTask("https://c.test", null, 0)));
    }
}

package com.crawler.worker;

import com.crawler.service.QueueService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JobContextTest {

    @Test
    void claimSlotCapsExactlyAtMaxPages() {
        CrawlJobContext ctx = new CrawlJobContext("j", 3, 1, new QueueService());
        assertEquals(0, ctx.claimSlot());
        assertEquals(1, ctx.claimSlot());
        assertEquals(2, ctx.claimSlot());
        assertEquals(3, ctx.claimSlot());
        assertEquals(3, ctx.claimSlot());
        assertEquals(3, ctx.getPages());
    }

    @Test
    void markWorkerDoneTrueExactlyOnceForLastWorker() {
        CrawlJobContext ctx = new CrawlJobContext("j", 5, 2, new QueueService());
        assertFalse(ctx.markWorkerDone());
        assertTrue(ctx.markWorkerDone());
        assertFalse(ctx.markWorkerDone());
    }

    @Test
    void drainedPredicateTracksInflightAndQueue() {
        CrawlJobContext ctx = new CrawlJobContext("j", 5, 1, new QueueService());
        assertTrue(ctx.isDrained());
        ctx.enterFetch();
        assertFalse(ctx.isDrained());
        ctx.exitFetch();
        assertTrue(ctx.isDrained());
    }
}

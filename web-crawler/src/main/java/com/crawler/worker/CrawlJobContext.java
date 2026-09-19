package com.crawler.worker;

import com.crawler.model.CrawlTask;
import com.crawler.service.QueueService;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CrawlJobContext {

    private final String jobId;
    private final int maxPages;
    private final QueueService queueService;
    private final AtomicInteger pages = new AtomicInteger(0);
    private final AtomicInteger urls = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger liveWorkers;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public CrawlJobContext(String jobId, int maxPages, int workers, QueueService queueService) {
        this.jobId = jobId;
        this.maxPages = maxPages;
        this.liveWorkers = new AtomicInteger(workers);
        this.queueService = queueService;
    }

    public String getJobId() { return jobId; }
    public int getMaxPages() { return maxPages; }

    public void addTask(CrawlTask task) { queueService.addTask(jobId, task); }

    public CrawlTask pollTask(long timeoutMs) {
        try {
            return queueService.pollTask(jobId, timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public boolean queueEmpty() { return queueService.queueSize(jobId) == 0; }

    public int claimSlot() { return pages.getAndUpdate(x -> x < maxPages ? x + 1 : x); }

    public int getPages() { return pages.get(); }
    public int getUrls() { return urls.get(); }
    public int getFailed() { return failed.get(); }
    public int getInFlight() { return inFlight.get(); }

    public void addUrls(int n) { urls.addAndGet(n); }
    public void countFailed() { failed.incrementAndGet(); }
    public void enterFetch() { inFlight.incrementAndGet(); }
    public void exitFetch() { inFlight.decrementAndGet(); }

    public boolean isDrained() { return inFlight.get() == 0 && queueEmpty(); }

    public boolean isRunning() { return running.get(); }
    public void stop() { running.set(false); }

    public boolean markWorkerDone() {
        if (liveWorkers.decrementAndGet() == 0) return finished.compareAndSet(false, true);
        return false;
    }
}

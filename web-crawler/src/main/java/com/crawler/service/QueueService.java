package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentLinkedQueue<CrawlTask> queue = new ConcurrentLinkedQueue<>();

    // Per-job queue registry (multithreading phase; Task 2 owns the full
    // replacement that deletes the global queue above). Added additively here
    // so CrawlJobContext (Task 1) compiles; method bodies are byte-identical
    // to the Task 2 plan spec.
    private final ConcurrentHashMap<String, LinkedBlockingQueue<CrawlTask>> queues = new ConcurrentHashMap<>();

    public void createQueue(String jobId) {
        queues.putIfAbsent(jobId, new LinkedBlockingQueue<>());
    }

    public void removeQueue(String jobId) {
        queues.remove(jobId);
    }

    public void addTask(String jobId, CrawlTask task) {
        queueFor(jobId).add(task);
        log.debug("Enqueued URL: {} (queue size: {})", task.getUrl(), queueFor(jobId).size());
    }

    public CrawlTask pollTask(String jobId, long timeoutMs) throws InterruptedException {
        return queueFor(jobId).poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public int queueSize(String jobId) {
        LinkedBlockingQueue<CrawlTask> q = queues.get(jobId);
        return q != null ? q.size() : 0;
    }

    private LinkedBlockingQueue<CrawlTask> queueFor(String jobId) {
        LinkedBlockingQueue<CrawlTask> q = queues.get(jobId);
        if (q == null) throw new IllegalStateException("No queue for job: " + jobId);
        return q;
    }

    public void addTask(CrawlTask task) {
        queue.add(task);
        log.debug("Enqueued URL: {} (queue size: {})", task.getUrl(), queue.size());
    }

    public CrawlTask pollTask() {
        return queue.poll();
    }

    public void addUrl(String url) {
        addTask(new CrawlTask(url, null, 0));
    }

    public String pollUrl() {
        CrawlTask task = pollTask();
        return task != null ? task.getUrl() : null;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
        log.info("URL queue cleared");
    }
}

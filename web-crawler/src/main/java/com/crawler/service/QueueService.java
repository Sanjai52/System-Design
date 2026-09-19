package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

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
}

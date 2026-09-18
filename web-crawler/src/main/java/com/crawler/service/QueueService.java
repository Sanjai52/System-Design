package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentLinkedQueue<CrawlTask> queue = new ConcurrentLinkedQueue<>();

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

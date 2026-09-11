package com.crawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void addUrl(String url) {
        queue.add(url);
        log.debug("Enqueued URL: {} (queue size: {})", url, queue.size());
    }

    public String pollUrl() {
        return queue.poll();
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

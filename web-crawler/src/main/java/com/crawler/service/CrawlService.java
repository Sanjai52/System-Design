package com.crawler.service;

import com.crawler.config.CrawlerConfig;
import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.worker.CrawlWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final CrawlerConfig config;
    private final QueueService queueService;
    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;
    private final CrawlStateRepository crawlStateRepository;

    private final Map<String, CrawlWorker> activeWorkers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> activeExecutors = new ConcurrentHashMap<>();

    public CrawlService(CrawlerConfig config,
                        QueueService queueService,
                        PageFetcherService pageFetcherService,
                        UrlDiscoveryService urlDiscoveryService,
                        CrawlStateRepository crawlStateRepository) {
        this.config = config;
        this.queueService = queueService;
        this.pageFetcherService = pageFetcherService;
        this.urlDiscoveryService = urlDiscoveryService;
        this.crawlStateRepository = crawlStateRepository;
    }

    public CrawlJob startCrawl(String seedUrl, int maxPages) {
        String jobId = UUID.randomUUID().toString();

        // Validate seed URL
        if (!urlDiscoveryService.isValidUrl(seedUrl)) {
            throw new IllegalArgumentException("Invalid seed URL: " + seedUrl);
        }

        // Create job
        CrawlJob job = new CrawlJob(jobId, seedUrl, maxPages);
        job.setStatus(CrawlStatus.QUEUED);
        crawlStateRepository.saveJob(job);

        // Clear any previous queue state
        queueService.clear();

        // Mark seed URL as visited and add to queue
        crawlStateRepository.markVisited(jobId, seedUrl);
        queueService.addUrl(seedUrl);

        // Create worker and executor
        CrawlWorker worker = new CrawlWorker(
                jobId, maxPages,
                queueService, pageFetcherService,
                urlDiscoveryService, crawlStateRepository
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        activeWorkers.put(jobId, worker);
        activeExecutors.put(jobId, executor);

        // Update status and start
        crawlStateRepository.updateJobStatus(jobId, CrawlStatus.CRAWLING);

        executor.submit(() -> {
            try {
                worker.run();
            } finally {
                crawlStateRepository.completeJob(jobId);
                activeWorkers.remove(jobId);
                activeExecutors.remove(jobId);
                log.info("Crawl job {} completed", jobId);
            }
        });

        log.info("Started crawl job {} for seed URL: {}", jobId, seedUrl);
        return job;
    }

    public CrawlJob getJobStatus(String jobId) {
        return crawlStateRepository.getJob(jobId);
    }

    public void stopCrawl(String jobId) {
        CrawlWorker worker = activeWorkers.get(jobId);
        ExecutorService executor = activeExecutors.get(jobId);

        if (worker != null) {
            worker.stop();
        }
        if (executor != null) {
            executor.shutdownNow();
        }

        crawlStateRepository.updateJobStatus(jobId, CrawlStatus.COMPLETED);
        log.info("Stopped crawl job: {}", jobId);
    }
}

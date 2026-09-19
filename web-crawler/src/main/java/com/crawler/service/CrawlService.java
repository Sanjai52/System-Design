package com.crawler.service;

import com.crawler.config.CrawlerConfig;
import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.CrawlTask;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.worker.CrawlJobContext;
import com.crawler.worker.CrawlWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    public record JobHandle(CrawlJobContext context, ExecutorService executor) {}

    private final CrawlerConfig config;
    private final QueueService queueService;
    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;
    private final CrawlStateRepository crawlStateRepository;

    private final Map<String, JobHandle> activeJobs = new ConcurrentHashMap<>();

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

        if (!urlDiscoveryService.isValidUrl(seedUrl)) {
            throw new IllegalArgumentException("Invalid seed URL: " + seedUrl);
        }

        CrawlJob job = new CrawlJob(jobId, seedUrl, maxPages);
        job.setStatus(CrawlStatus.QUEUED);
        crawlStateRepository.saveJob(job);

        int workers = Math.max(1, config.getThreadCount());
        queueService.createQueue(jobId);
        CrawlJobContext context = new CrawlJobContext(jobId, maxPages, workers, queueService);
        context.addTask(new CrawlTask(seedUrl, null, 0));
        crawlStateRepository.markVisited(jobId, seedUrl);

        ExecutorService executor = Executors.newFixedThreadPool(workers, new CrawlThreadFactory(jobId));
        activeJobs.put(jobId, new JobHandle(context, executor));

        crawlStateRepository.updateJobStatus(jobId, CrawlStatus.CRAWLING);

        Runnable finishHook = () -> {
            queueService.removeQueue(jobId);
            activeJobs.remove(jobId);
            executor.shutdown();
            log.info("Crawl job {} completed", jobId);
        };
        for (int i = 0; i < workers; i++) {
            executor.submit(new CrawlWorker(jobId, context, pageFetcherService,
                    urlDiscoveryService, crawlStateRepository, finishHook));
        }

        log.info("Started crawl job {} for seed URL: {} ({} workers)", jobId, seedUrl, workers);
        return job;
    }

    public CrawlJob getJobStatus(String jobId) {
        return crawlStateRepository.getJob(jobId);
    }

    public void stopCrawl(String jobId) {
        JobHandle handle = activeJobs.get(jobId);
        if (handle != null) {
            handle.context().stop();
            handle.executor().shutdownNow();
        }

        crawlStateRepository.updateJobStatus(jobId, CrawlStatus.COMPLETED);
        log.info("Stopped crawl job: {}", jobId);
    }

    private static final class CrawlThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        CrawlThreadFactory(String jobId) {
            this.prefix = "crawl-" + jobId.substring(0, 8) + "-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}

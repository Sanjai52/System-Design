package com.crawler.worker;

import com.crawler.model.CrawlStatus;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.QueueService;
import com.crawler.service.UrlDiscoveryService;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CrawlWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CrawlWorker.class);

    private final String jobId;
    private final int maxPages;
    private final QueueService queueService;
    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;
    private final CrawlStateRepository crawlStateRepository;

    private volatile boolean running = true;
    private int pagesCrawled = 0;
    private int urlsDiscovered = 0;
    private int failedCount = 0;

    public CrawlWorker(String jobId, int maxPages,
                       QueueService queueService,
                       PageFetcherService pageFetcherService,
                       UrlDiscoveryService urlDiscoveryService,
                       CrawlStateRepository crawlStateRepository) {
        this.jobId = jobId;
        this.maxPages = maxPages;
        this.queueService = queueService;
        this.pageFetcherService = pageFetcherService;
        this.urlDiscoveryService = urlDiscoveryService;
        this.crawlStateRepository = crawlStateRepository;
    }

    @Override
    public void run() {
        log.info("CrawlWorker started for job: {}", jobId);

        while (running && pagesCrawled < maxPages) {
            try {
                String url = queueService.pollUrl();
                if (url == null) {
                    // Wait briefly then check again — queue might get new URLs
                    Thread.sleep(200);
                    continue;
                }

                pagesCrawled++;
                crawlStateRepository.updateJobProgress(jobId, pagesCrawled, urlsDiscovered);

                processUrl(url);

                // Small delay to be respectful to target servers
                Thread.sleep(100);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("CrawlWorker interrupted for job: {}", jobId);
                break;
            } catch (Exception e) {
                log.error("Unexpected error in CrawlWorker for job: {}", jobId, e);
            }
        }

        // Mark job as completed
        crawlStateRepository.updateJobProgress(jobId, pagesCrawled, urlsDiscovered);
        log.info("CrawlWorker finished for job: {} (pages: {}, discovered: {}, failed: {})",
                jobId, pagesCrawled, urlsDiscovered, failedCount);
    }

    private void processUrl(String url) {
        log.info("Processing URL #{}: {}", pagesCrawled, url);

        // Mark as crawling
        crawlStateRepository.saveUrlResult(jobId,
                new UrlResult(url, CrawlStatus.CRAWLING, 0));

        try {
            // Fetch page
            Document document = pageFetcherService.fetchPage(url);
            log.info("Fetched page: {} (title: {})", url, document.title());

            // Extract links
            List<String> discoveredUrls = urlDiscoveryService.extractLinks(document, url);
            int linkCount = discoveredUrls.size();
            urlsDiscovered += linkCount;

            // Check each discovered URL
            int newUrls = 0;
            for (String discoveredUrl : discoveredUrls) {
                // Atomic duplicate check: SADD returns 1 if new, 0 if exists
                if (crawlStateRepository.markVisited(jobId, discoveredUrl)) {
                    queueService.addUrl(discoveredUrl);
                    newUrls++;
                }
            }

            // Save successful result
            crawlStateRepository.saveUrlResult(jobId,
                    new UrlResult(url, CrawlStatus.COMPLETED, linkCount));

            log.info("Completed URL: {} (links: {}, new: {})", url, linkCount, newUrls);

        } catch (Exception e) {
            failedCount++;
            String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("FAILED URL #{}: {} — {}", failedCount, url, errorMsg);

            UrlResult failedResult = new UrlResult(url, CrawlStatus.FAILED, 0);
            failedResult.setError(errorMsg);
            crawlStateRepository.saveUrlResult(jobId, failedResult);
        }
    }

    public void stop() {
        this.running = false;
    }

    public int getPagesCrawled() { return pagesCrawled; }
    public int getUrlsDiscovered() { return urlsDiscovered; }
}

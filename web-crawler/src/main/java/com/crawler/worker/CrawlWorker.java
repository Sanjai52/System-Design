package com.crawler.worker;

import com.crawler.model.CrawlStatus;
import com.crawler.model.CrawlTask;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.UrlDiscoveryService;
import com.crawler.service.TextTokenizer;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CrawlWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CrawlWorker.class);

    private final String jobId;
    private final CrawlJobContext context;
    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;
    private final CrawlStateRepository crawlStateRepository;
    private final Runnable finishHook;

    public CrawlWorker(String jobId, CrawlJobContext context,
                       PageFetcherService pageFetcherService,
                       UrlDiscoveryService urlDiscoveryService,
                       CrawlStateRepository crawlStateRepository,
                       Runnable finishHook) {
        this.jobId = jobId;
        this.context = context;
        this.pageFetcherService = pageFetcherService;
        this.urlDiscoveryService = urlDiscoveryService;
        this.crawlStateRepository = crawlStateRepository;
        this.finishHook = finishHook;
    }

    @Override
    public void run() {
        log.info("CrawlWorker started for job: {}", jobId);

        try {
            while (context.isRunning()) {
                if (context.getPages() >= context.getMaxPages()) break;

                CrawlTask task = context.pollTask(200);
                if (task == null) {
                    if (!context.isRunning() || Thread.currentThread().isInterrupted()) break;
                    if (context.isDrained()) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (context.isDrained()) break;
                    }
                    continue;
                }

                int slot = context.claimSlot();
                if (slot >= context.getMaxPages()) {
                    context.addTask(task);
                    break;
                }

                context.enterFetch();
                try {
                    processUrl(task);
                } finally {
                    context.exitFetch();
                }
                crawlStateRepository.updateJobProgress(jobId, context.getPages(), context.getUrls());

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            if (context.markWorkerDone()) {
                try {
                    crawlStateRepository.updateJobProgress(jobId, context.getPages(), context.getUrls());
                    crawlStateRepository.completeJob(jobId);
                    log.info("Crawl job {} completed (pages: {}, discovered: {}, failed: {})",
                            jobId, context.getPages(), context.getUrls(), context.getFailed());
                } finally {
                    finishHook.run();
                }
            }
        }
    }

    private void processUrl(CrawlTask task) {
        String url = task.getUrl();
        log.info("Processing URL: {}", url);

        UrlResult crawling = new UrlResult(url, CrawlStatus.CRAWLING, 0);
        crawling.setParentUrl(task.getParentUrl());
        crawling.setDepth(task.getDepth());
        crawlStateRepository.saveUrlResult(jobId, crawling);

        try {
            Document document = pageFetcherService.fetchPage(url);
            log.info("Fetched page: {} (title: {})", url, document.title());

            List<String> discoveredUrls = urlDiscoveryService.extractLinks(document, url);
            int linkCount = discoveredUrls.size();
            context.addUrls(linkCount);
            try {
                String pageText = document.body() != null ? document.body().text() : "";
                String pageTitle = document.title() != null ? document.title() : "";
                java.util.Map<String, Integer> termFreq = TextTokenizer.tokenize(pageText);
                int totalTokens = termFreq.values().stream().mapToInt(Integer::intValue).sum();
                String snippet = pageText.length() > 200 ? pageText.substring(0, 200) : pageText;
                crawlStateRepository.indexPage(url, jobId, pageTitle, snippet,
                        Math.max(1, totalTokens), termFreq);
            } catch (Exception ie) {
                log.warn("Indexing failed for {}: {}", url, ie.getMessage());
            }

            java.util.List<String> newChildren = new java.util.ArrayList<>();
            for (String discoveredUrl : discoveredUrls) {
                if (crawlStateRepository.markVisited(jobId, discoveredUrl)) {
                    context.addTask(new CrawlTask(discoveredUrl, url, task.getDepth() + 1));
                    newChildren.add(discoveredUrl);
                }
            }

            UrlResult done = new UrlResult(url, CrawlStatus.COMPLETED, linkCount);
            done.setParentUrl(task.getParentUrl());
            done.setDepth(task.getDepth());
            done.setChildUrls(newChildren);
            crawlStateRepository.saveUrlResult(jobId, done);

            log.info("Completed URL: {} (links: {}, new: {})", url, linkCount, newChildren.size());

        } catch (Exception e) {
            context.countFailed();
            String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("FAILED URL: {} — {}", url, errorMsg);

            UrlResult failedResult = new UrlResult(url, CrawlStatus.FAILED, 0);
            failedResult.setParentUrl(task.getParentUrl());
            failedResult.setDepth(task.getDepth());
            failedResult.setError(errorMsg);
            crawlStateRepository.saveUrlResult(jobId, failedResult);
        }
    }

    public void stop() {
        context.stop();
    }

    public int getPagesCrawled() { return context.getPages(); }
    public int getUrlsDiscovered() { return context.getUrls(); }
}

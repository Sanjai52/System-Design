package com.crawler.controller;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlRequest;
import com.crawler.model.CrawlResponse;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.CrawlService;
import com.crawler.service.HierarchyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/crawl")
public class CrawlController {

    private static final Logger log = LoggerFactory.getLogger(CrawlController.class);

    private final CrawlService crawlService;
    private final CrawlStateRepository crawlStateRepository;
    private final HierarchyService hierarchyService;

    public CrawlController(CrawlService crawlService, CrawlStateRepository crawlStateRepository, HierarchyService hierarchyService) {
        this.crawlService = crawlService;
        this.crawlStateRepository = crawlStateRepository;
        this.hierarchyService = hierarchyService;
    }

    @PostMapping
    public ResponseEntity<CrawlResponse> startCrawl(@RequestBody CrawlRequest request) {
        log.info("Received crawl request for URL: {}", request.getUrl());

        try {
            CrawlJob job = crawlService.startCrawl(request.getUrl(), request.getMaxPages());
            CrawlResponse response = new CrawlResponse(
                    job.getJobId(),
                    job.getStatus().name(),
                    "Crawl job started successfully"
            );
            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            CrawlResponse error = new CrawlResponse(null, "REJECTED", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("Failed to start crawl job", e);
            CrawlResponse error = new CrawlResponse(null, "ERROR", "Internal server error");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        CrawlJob job = crawlService.getJobStatus(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = Map.of(
                "jobId", job.getJobId(),
                "seedUrl", job.getSeedUrl() != null ? job.getSeedUrl() : "",
                "maxPages", job.getMaxPages(),
                "pagesCrawled", job.getPagesCrawled(),
                "urlsDiscovered", job.getUrlsDiscovered(),
                "status", job.getStatus().name()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{jobId}/urls")
    public ResponseEntity<List<UrlResult>> getJobUrls(@PathVariable String jobId) {
        CrawlJob job = crawlService.getJobStatus(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        List<UrlResult> results = crawlStateRepository.getUrlResults(jobId);
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, String>> stopJob(@PathVariable String jobId) {
        CrawlJob job = crawlService.getJobStatus(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        crawlService.stopCrawl(jobId);
        return ResponseEntity.ok(Map.of("message", "Job stopped", "jobId", jobId));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchBySeed(@RequestParam String seedUrl) {
        try {
            return ResponseEntity.ok(hierarchyService.searchBySeed(seedUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatestJob() {
        return hierarchyService.getLatestJob()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "No crawl jobs found")));
    }

    @GetMapping("/{jobId}/hierarchy")
    public ResponseEntity<?> getHierarchy(@PathVariable String jobId,
                                          @RequestParam(required = false) Integer maxDepth) {
        try {
            com.crawler.model.HierarchyNode tree = hierarchyService.buildTree(jobId, maxDepth);
            CrawlJob job = crawlService.getJobStatus(jobId);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("jobId", jobId);
            body.put("seedUrl", job != null ? job.getSeedUrl() : null);
            body.put("status", job != null ? job.getStatus().name() : null);
            body.put("tree", tree);
            return ResponseEntity.ok(body);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

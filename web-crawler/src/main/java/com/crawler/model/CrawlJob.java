package com.crawler.model;

import java.time.LocalDateTime;

public class CrawlJob {

    private String jobId;
    private String seedUrl;
    private int maxPages;
    private int pagesCrawled;
    private int urlsDiscovered;
    private CrawlStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public CrawlJob() {}

    public CrawlJob(String jobId, String seedUrl, int maxPages) {
        this.jobId = jobId;
        this.seedUrl = seedUrl;
        this.maxPages = maxPages;
        this.pagesCrawled = 0;
        this.urlsDiscovered = 0;
        this.status = CrawlStatus.DISCOVERED;
        this.startTime = LocalDateTime.now();
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getSeedUrl() { return seedUrl; }
    public void setSeedUrl(String seedUrl) { this.seedUrl = seedUrl; }

    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }

    public int getPagesCrawled() { return pagesCrawled; }
    public void setPagesCrawled(int pagesCrawled) { this.pagesCrawled = pagesCrawled; }

    public int getUrlsDiscovered() { return urlsDiscovered; }
    public void setUrlsDiscovered(int urlsDiscovered) { this.urlsDiscovered = urlsDiscovered; }

    public CrawlStatus getStatus() { return status; }
    public void setStatus(CrawlStatus status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}

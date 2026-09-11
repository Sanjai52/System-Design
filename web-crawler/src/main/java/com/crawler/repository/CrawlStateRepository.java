package com.crawler.repository;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.UrlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class CrawlStateRepository {

    private static final Logger log = LoggerFactory.getLogger(CrawlStateRepository.class);
    private static final String VISITED_PREFIX = "crawl:visited:";
    private static final String JOB_PREFIX = "crawl:job:";
    private static final String RESULT_PREFIX = "crawl:result:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RedisTemplate<String, Object> redisTemplate;

    public CrawlStateRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // --- Visited URL Set (SADD for atomic duplicate detection) ---

    public boolean markVisited(String jobId, String url) {
        String key = VISITED_PREFIX + jobId;
        Long added = redisTemplate.opsForSet().add(key, url);
        return added != null && added > 0;
    }

    public boolean isVisited(String jobId, String url) {
        String key = VISITED_PREFIX + jobId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, url));
    }

    public Set<Object> getAllVisited(String jobId) {
        String key = VISITED_PREFIX + jobId;
        return redisTemplate.opsForSet().members(key);
    }

    public long getVisitedCount(String jobId) {
        String key = VISITED_PREFIX + jobId;
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size : 0;
    }

    // --- Crawl Job Metadata (HASH) ---

    public void saveJob(CrawlJob job) {
        String key = JOB_PREFIX + job.getJobId();
        Map<String, String> fields = Map.of(
                "seedUrl", job.getSeedUrl(),
                "maxPages", String.valueOf(job.getMaxPages()),
                "pagesCrawled", String.valueOf(job.getPagesCrawled()),
                "urlsDiscovered", String.valueOf(job.getUrlsDiscovered()),
                "status", job.getStatus().name(),
                "startTime", job.getStartTime().format(FORMATTER)
        );
        redisTemplate.opsForHash().putAll(key, fields);
    }

    public void updateJobStatus(String jobId, CrawlStatus status) {
        String key = JOB_PREFIX + jobId;
        redisTemplate.opsForHash().put(key, "status", status.name());
    }

    public void updateJobProgress(String jobId, int pagesCrawled, int urlsDiscovered) {
        String key = JOB_PREFIX + jobId;
        redisTemplate.opsForHash().put(key, "pagesCrawled", String.valueOf(pagesCrawled));
        redisTemplate.opsForHash().put(key, "urlsDiscovered", String.valueOf(urlsDiscovered));
    }

    public void completeJob(String jobId) {
        String key = JOB_PREFIX + jobId;
        redisTemplate.opsForHash().put(key, "status", CrawlStatus.COMPLETED.name());
        redisTemplate.opsForHash().put(key, "endTime", LocalDateTime.now().format(FORMATTER));
    }

    public CrawlJob getJob(String jobId) {
        String key = JOB_PREFIX + jobId;
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
        if (fields.isEmpty()) return null;

        CrawlJob job = new CrawlJob();
        job.setJobId(jobId);
        job.setSeedUrl((String) fields.get("seedUrl"));
        job.setMaxPages(Integer.parseInt((String) fields.get("maxPages")));
        job.setPagesCrawled(Integer.parseInt((String) fields.get("pagesCrawled")));
        job.setUrlsDiscovered(Integer.parseInt((String) fields.get("urlsDiscovered")));
        job.setStatus(CrawlStatus.valueOf((String) fields.get("status")));
        if (fields.containsKey("startTime")) {
            job.setStartTime(LocalDateTime.parse((String) fields.get("startTime"), FORMATTER));
        }
        if (fields.containsKey("endTime")) {
            job.setEndTime(LocalDateTime.parse((String) fields.get("endTime"), FORMATTER));
        }
        return job;
    }

    // --- Per-URL Crawl Result (HASH) ---

    public void saveUrlResult(String jobId, UrlResult result) {
        String urlKey = generateUrlKey(result.getUrl());
        String key = RESULT_PREFIX + jobId + ":" + urlKey;
        Map<String, String> fields = new java.util.HashMap<>();
        fields.put("url", result.getUrl());
        fields.put("status", result.getStatus().name());
        fields.put("discoveredLinks", String.valueOf(result.getDiscoveredLinks()));
        fields.put("timestamp", result.getTimestamp().format(FORMATTER));
        if (result.getError() != null) {
            fields.put("error", result.getError());
        }
        redisTemplate.opsForHash().putAll(key, fields);
    }

    public List<UrlResult> getUrlResults(String jobId) {
        String pattern = RESULT_PREFIX + jobId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return List.of();

        return keys.stream().map(key -> {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
            UrlResult result = new UrlResult();
            result.setUrl((String) fields.get("url"));
            result.setStatus(CrawlStatus.valueOf((String) fields.get("status")));
            result.setDiscoveredLinks(Integer.parseInt((String) fields.get("discoveredLinks")));
            if (fields.containsKey("timestamp")) {
                result.setTimestamp(LocalDateTime.parse((String) fields.get("timestamp"), FORMATTER));
            }
            if (fields.containsKey("error")) {
                result.setError((String) fields.get("error"));
            }
            return result;
        }).collect(Collectors.toList());
    }

    // --- Cleanup ---

    public void deleteJob(String jobId) {
        redisTemplate.delete(VISITED_PREFIX + jobId);
        redisTemplate.delete(JOB_PREFIX + jobId);
        Set<String> resultKeys = redisTemplate.keys(RESULT_PREFIX + jobId + ":*");
        if (resultKeys != null) {
            redisTemplate.delete(resultKeys);
        }
    }

    private String generateUrlKey(String url) {
        return String.valueOf(url.hashCode());
    }
}

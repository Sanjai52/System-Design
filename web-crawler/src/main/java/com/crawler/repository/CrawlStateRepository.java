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
    private static final String TERM_PREFIX = "index:term:";
    private static final String DOC_PREFIX = "index:doc:";
    private static final String DOC_SET = "index:docs";
    private static final String META_KEY = "index:meta";
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
        fields.put("parentUrl", result.getParentUrl() != null ? result.getParentUrl() : "");
        fields.put("depth", String.valueOf(result.getDepth()));
        java.util.List<String> kids = result.getChildUrls() != null ? result.getChildUrls() : java.util.List.of();
        fields.put("childUrls", String.join("\n", kids));
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
            Object parentRaw = fields.get("parentUrl");
            String parent = parentRaw != null ? parentRaw.toString() : "";
            result.setParentUrl(parent.isEmpty() ? null : parent);
            Object depthRaw = fields.get("depth");
            int depth = 0;
            try { depth = depthRaw != null ? Integer.parseInt(depthRaw.toString()) : 0; } catch (NumberFormatException ignored) {}
            result.setDepth(depth);
            Object kidsRaw = fields.get("childUrls");
            java.util.List<String> kids = new java.util.ArrayList<>();
            if (kidsRaw != null && !kidsRaw.toString().isEmpty()) {
                for (String k : kidsRaw.toString().split("\n", -1)) {
                    if (!k.isEmpty()) kids.add(k);
                }
            }
            result.setChildUrls(kids);
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

    public java.util.List<CrawlJob> getAllJobs() {
        Set<String> keys = redisTemplate.keys(JOB_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return java.util.List.of();
        java.util.List<CrawlJob> jobs = new java.util.ArrayList<>();
        for (String key : keys) {
            String jobId = key.substring(JOB_PREFIX.length());
            CrawlJob job = getJob(jobId);
            if (job != null) jobs.add(job);
        }
        return jobs;
    }

    public java.util.List<CrawlJob> findJobsBySeedContains(String query) {
        if (query == null || query.isBlank()) return java.util.List.of();
        String q = query.toLowerCase();
        return getAllJobs().stream()
                .filter(j -> j.getSeedUrl() != null && j.getSeedUrl().toLowerCase().contains(q))
                .collect(java.util.stream.Collectors.toList());
    }

    public void indexPage(String url, String jobId, String title, String snippet, int length,
                      java.util.Map<String, Integer> tf) {
        if (url == null || url.isBlank() || tf == null || tf.isEmpty()) return;
        for (java.util.Map.Entry<String, Integer> e : tf.entrySet()) {
            redisTemplate.opsForZSet().add(TERM_PREFIX + e.getKey(), url, e.getValue().doubleValue());
        }
        String docKey = DOC_PREFIX + generateUrlKey(url);
        java.util.Map<String, String> doc = new java.util.HashMap<>();
        doc.put("url", url);
        doc.put("title", title != null ? title : "");
        doc.put("snippet", snippet != null ? snippet : "");
        doc.put("length", String.valueOf(length));
        doc.put("jobId", jobId != null ? jobId : "");
        redisTemplate.opsForHash().putAll(docKey, doc);
        Long added = redisTemplate.opsForSet().add(DOC_SET, url);
        if (added != null && added > 0) {
            redisTemplate.opsForHash().increment(META_KEY, "totalDocs", 1);
        }
    }

    public java.util.Map<String, Double> getTermScores(String token) {
        java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().rangeWithScores(TERM_PREFIX + token, 0, -1);
        java.util.Map<String, Double> out = new java.util.HashMap<>();
        if (tuples == null) return out;
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object> t : tuples) {
            if (t.getValue() != null && t.getScore() != null) out.put(t.getValue().toString(), t.getScore());
        }
        return out;
    }

    public long getTermDocCount(String token) {
        Long n = redisTemplate.opsForZSet().zCard(TERM_PREFIX + token);
        return n != null ? n : 0;
    }

    public long getTotalDocs() {
        Object v = redisTemplate.opsForHash().get(META_KEY, "totalDocs");
        if (v == null) return 0;
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public java.util.Map<String, String> getDoc(String url) {
        java.util.Map<Object, Object> raw = redisTemplate.opsForHash().entries(DOC_PREFIX + generateUrlKey(url));
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (java.util.Map.Entry<Object, Object> e : raw.entrySet()) {
            out.put(e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : "");
        }
        return out;
    }

    private String generateUrlKey(String url) {
        return String.valueOf(url.hashCode());
    }
}

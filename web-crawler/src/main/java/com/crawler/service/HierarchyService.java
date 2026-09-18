package com.crawler.service;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.HierarchyNode;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class HierarchyService {

    private final CrawlStateRepository repository;

    public HierarchyService(CrawlStateRepository repository) {
        this.repository = repository;
    }

    public HierarchyNode buildTree(String jobId, Integer maxDepth) {
        CrawlJob job = repository.getJob(jobId);
        if (job == null) throw new NoSuchElementException("Job not found: " + jobId);
        List<UrlResult> results = repository.getUrlResults(jobId);
        int limit = maxDepth != null ? maxDepth : Integer.MAX_VALUE;
        return buildTreeFrom(job.getSeedUrl(), job.getStatus(), results, limit);
    }

    public List<CrawlJob> searchBySeed(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("seedUrl query must not be blank");
        return repository.findJobsBySeedContains(query.trim());
    }

    static HierarchyNode buildTreeFrom(String seedUrl, CrawlStatus rootStatus, List<UrlResult> results, int maxDepth) {
        Map<String, UrlResult> byUrl = new HashMap<>();
        for (UrlResult r : results) {
            if (r.getUrl() != null) byUrl.putIfAbsent(r.getUrl(), r);
        }
        UrlResult seedResult = byUrl.get(seedUrl);
        CrawlStatus status = seedResult != null ? seedResult.getStatus() : rootStatus;
        int links = seedResult != null ? seedResult.getDiscoveredLinks() : 0;
        HierarchyNode root = new HierarchyNode(seedUrl, status, 0, links);
        Set<String> visited = new HashSet<>();
        visited.add(seedUrl);
        expand(root, byUrl, visited, maxDepth);
        return root;
    }

    private static void expand(HierarchyNode node, Map<String, UrlResult> byUrl, Set<String> visited, int maxDepth) {
        if (node.getDepth() >= maxDepth) return;
        UrlResult self = byUrl.get(node.getUrl());
        List<String> kids = self != null && self.getChildUrls() != null ? self.getChildUrls() : List.of();
        if (kids.isEmpty() && node.getDepth() == 0) {
            java.util.List<UrlResult> orphans = new java.util.ArrayList<>();
            for (UrlResult r : byUrl.values()) {
                boolean isSeed = r.getUrl() != null && r.getUrl().equals(node.getUrl());
                boolean noParent = r.getParentUrl() == null || r.getParentUrl().isEmpty();
                if (!isSeed && noParent) orphans.add(r);
            }
            orphans.sort(java.util.Comparator.comparing(UrlResult::getTimestamp,
                    java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
            for (UrlResult r : orphans) kids = append(kids, r.getUrl());
        }
        for (String childUrl : kids) {
            if (childUrl == null || childUrl.isEmpty()) continue;
            if (!visited.add(childUrl)) {
                HierarchyNode leaf = nodeFor(childUrl, byUrl, node.getDepth() + 1);
                leaf.setAlreadyVisited(true);
                node.getChildren().add(leaf);
                continue;
            }
            HierarchyNode child = nodeFor(childUrl, byUrl, node.getDepth() + 1);
            node.getChildren().add(child);
            expand(child, byUrl, visited, maxDepth);
        }
    }

    private static HierarchyNode nodeFor(String url, Map<String, UrlResult> byUrl, int depth) {
        UrlResult r = byUrl.get(url);
        if (r == null) return new HierarchyNode(url, CrawlStatus.DISCOVERED, depth, 0);
        return new HierarchyNode(url, r.getStatus(), depth, r.getDiscoveredLinks());
    }

    private static List<String> append(List<String> list, String value) {
        List<String> out = new ArrayList<>(list);
        out.add(value);
        return out;
    }
}

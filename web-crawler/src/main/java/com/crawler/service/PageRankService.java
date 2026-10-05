package com.crawler.service;

import com.crawler.model.CrawlJob;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PageRankService {

    public static final double DAMPING = 0.85;
    public static final int MAX_ITERATIONS = 100;
    public static final double EPSILON = 1e-6;

    private final CrawlStateRepository repository;

    public PageRankService(CrawlStateRepository repository) {
        this.repository = repository;
    }

    public static Map<String, Double> computeRanks(Map<String, Set<String>> outlinks) {
        Set<String> nodeSet = new HashSet<>(outlinks.keySet());
        for (Set<String> outs : outlinks.values()) {
            if (outs != null) nodeSet.addAll(outs);
        }
        List<String> nodes = new ArrayList<>(nodeSet);
        Collections.sort(nodes);
        int n = nodes.size();
        Map<String, Double> ranks = new HashMap<>();
        if (n == 0) return ranks;

        Map<String, Set<String>> links = new HashMap<>();
        for (String u : nodes) {
            Set<String> outs = outlinks.getOrDefault(u, Set.of());
            Set<String> known = new HashSet<>();
            if (outs != null) {
                for (String v : outs) {
                    if (v != null && nodeSet.contains(v)) known.add(v);
                }
            }
            links.put(u, known);
        }

        for (String u : nodes) ranks.put(u, 1.0 / n);
        Map<String, List<String>> inlinks = new HashMap<>();
        for (String v : nodes) inlinks.put(v, new ArrayList<>());
        for (String u : nodes) {
            Set<String> outs = links.get(u);
            if (!outs.isEmpty()) {
                for (String v : outs) inlinks.get(v).add(u);
            }
        }
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double danglingSum = 0.0;
            for (String u : nodes) {
                if (links.get(u).isEmpty()) danglingSum += ranks.get(u);
            }
            Map<String, Double> next = new HashMap<>();
            double maxDelta = 0.0;
            for (String v : nodes) {
                double inflow = danglingSum / n;
                for (String u : inlinks.get(v)) inflow += ranks.get(u) / links.get(u).size();
                double nv = (1.0 - DAMPING) / n + DAMPING * inflow;
                next.put(v, nv);
                maxDelta = Math.max(maxDelta, Math.abs(nv - ranks.get(v)));
            }
            ranks = next;
            if (maxDelta < EPSILON) break;
        }
        return ranks;
    }

    public Map<String, Set<String>> buildGraph() {
        Map<String, Set<String>> graph = new HashMap<>();
        for (CrawlJob job : repository.getAllJobs()) {
            for (UrlResult r : repository.getUrlResults(job.getJobId())) {
                if (r.getUrl() == null || r.getUrl().isEmpty()) continue;
                Set<String> kids = new HashSet<>();
                if (r.getChildUrls() != null) {
                    for (String k : r.getChildUrls()) {
                        if (k != null && !k.isEmpty()) kids.add(k);
                    }
                }
                graph.merge(r.getUrl(), kids, (a, b) -> {
                    a.addAll(b);
                    return a;
                });
                for (String k : kids) graph.putIfAbsent(k, new HashSet<>());
            }
        }
        return graph;
    }

    public int recomputeAll() {
        Map<String, Double> ranks = computeRanks(buildGraph());
        repository.savePageRanks(ranks);
        return ranks.size();
    }

    public double getRank(String url) {
        return repository.getPageRank(url);
    }
}

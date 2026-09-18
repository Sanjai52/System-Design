package com.crawler.service;

import com.crawler.model.SearchHit;
import com.crawler.repository.CrawlStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankService {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;

    private final CrawlStateRepository repository;

    public RankService(CrawlStateRepository repository) {
        this.repository = repository;
    }

    public List<SearchHit> search(String query, Integer limit) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Search query must not be blank");
        int n = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Map<String, Integer> qtokens = TextTokenizer.tokenize(query);
        if (qtokens.isEmpty()) return List.of();
        long totalDocs = repository.getTotalDocs();
        Map<String, Map<String, Double>> postings = new HashMap<>();
        Map<String, Long> df = new HashMap<>();
        Map<String, Integer> docLen = new HashMap<>();
        for (String t : qtokens.keySet()) {
            Map<String, Double> scores = repository.getTermScores(t);
            if (scores.isEmpty()) continue;
            postings.put(t, scores);
            df.put(t, repository.getTermDocCount(t));
            for (String url : scores.keySet()) {
                docLen.computeIfAbsent(url, u -> {
                    String l = repository.getDoc(u).getOrDefault("length", "0");
                    try {
                        return Integer.parseInt(l);
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                });
            }
        }
        return combineScores(postings, docLen, totalDocs, df, n, repository);
    }

    static List<SearchHit> combineScores(Map<String, Map<String, Double>> postings,
                                         Map<String, Integer> docLen,
                                         long totalDocs,
                                         Map<String, Long> df,
                                         int limit,
                                         CrawlStateRepository repository) {
        Map<String, Double> idf = new HashMap<>();
        boolean anyIdf = false;
        for (String t : postings.keySet()) {
            long d = df.getOrDefault(t, 0L);
            double v = (d > 0 && totalDocs > d) ? Math.log((double) totalDocs / d) : 0.0;
            idf.put(t, v);
            if (v > 0) anyIdf = true;
        }
        Map<String, Double> agg = new HashMap<>();
        for (String t : postings.keySet()) {
            for (Map.Entry<String, Double> e : postings.get(t).entrySet()) {
                String url = e.getKey();
                double tf = e.getValue() / Math.max(1, docLen.getOrDefault(url, 1));
                double w = anyIdf ? idf.get(t) : 1.0;
                agg.merge(url, tf * w, Double::sum);
            }
        }
        List<String> ranked = new ArrayList<>(agg.keySet());
        ranked.sort(Comparator.comparingDouble((String u) -> agg.get(u)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        List<SearchHit> out = new ArrayList<>();
        for (String url : ranked.subList(0, Math.min(limit, ranked.size()))) {
            Map<String, String> doc = repository.getDoc(url);
            out.add(new SearchHit(url, doc.getOrDefault("title", ""),
                    doc.getOrDefault("snippet", ""), agg.get(url)));
        }
        return out;
    }
}

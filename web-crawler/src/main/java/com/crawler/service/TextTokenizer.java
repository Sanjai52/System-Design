package com.crawler.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TextTokenizer {

    public static final int MIN_TOKEN_LENGTH = 3;
    public static final int MAX_TOKENS_PER_PAGE = 10000;

    public static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for",
            "with", "is", "are", "was", "were", "be", "been", "by", "as", "at",
            "from", "that", "this", "it", "its", "into", "over", "after", "before",
            "between", "through", "during", "such", "other", "than", "then", "there",
            "their", "what", "which", "when", "where", "while", "about", "also",
            "just", "like", "more", "most", "only", "own", "same", "still", "even");

    public static Map<String, Integer> tokenize(String text) {
        Map<String, Integer> freq = new HashMap<>();
        if (text == null || text.isBlank()) return freq;
        String[] parts = text.toLowerCase().split("[^a-z0-9]+");
        int total = 0;
        for (String p : parts) {
            if (total >= MAX_TOKENS_PER_PAGE) break;
            if (p.length() < MIN_TOKEN_LENGTH) continue;
            if (STOP_WORDS.contains(p)) continue;
            freq.merge(p, 1, Integer::sum);
            total++;
        }
        return freq;
    }
}

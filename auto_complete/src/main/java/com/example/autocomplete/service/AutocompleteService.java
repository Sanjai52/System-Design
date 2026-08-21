package com.example.autocomplete.service;

import com.example.autocomplete.model.SearchTerm;
import com.example.autocomplete.trie.TrieService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutocompleteService {

    private final TrieService trieService;
    private final CacheService cacheService;

    @Value("${autocomplete.default.top-k:10}")
    private int defaultTopK;

    public AutocompleteService(TrieService trieService, CacheService cacheService) {
        this.trieService = trieService;
        this.cacheService = cacheService;
    }

    public AutocompleteResult getSuggestions(String prefix, int k) {
        if (prefix == null || prefix.isBlank()) {
            return new AutocompleteResult(List.of(), false);
        }

        String normalizedPrefix = prefix.toLowerCase().trim();

        List<SearchTerm> cached = cacheService.getCachedSuggestions(normalizedPrefix);
        if (cached != null) {
            return new AutocompleteResult(cached, true);
        }

        List<SearchTerm> suggestions = trieService.getTopK(normalizedPrefix, k > 0 ? k : defaultTopK);
        cacheService.cacheSuggestions(normalizedPrefix, suggestions);
        return new AutocompleteResult(suggestions, false);
    }

    public AutocompleteResult getSuggestions(String prefix) {
        return getSuggestions(prefix, defaultTopK);
    }

    public static class AutocompleteResult {
        private final List<SearchTerm> suggestions;
        private final boolean cached;

        public AutocompleteResult(List<SearchTerm> suggestions, boolean cached) {
            this.suggestions = suggestions;
            this.cached = cached;
        }

        public List<SearchTerm> getSuggestions() {
            return suggestions;
        }

        public boolean isCached() {
            return cached;
        }
    }
}

package com.example.autocomplete.trie;

import com.example.autocomplete.model.SearchTerm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class TrieService {

    private final TrieNode root;

    public TrieService() {
        this.root = new TrieNode();
    }

    public void insert(String term, long frequency) {
        TrieNode current = root;
        for (char c : term.toLowerCase().toCharArray()) {
            current.getChildren().computeIfAbsent(c, k -> new TrieNode());
            current = current.getChildren().get(c);
        }
        current.setEndOfWord(true);
        current.setFrequency(frequency);
    }

    public List<SearchTerm> getTopK(String prefix, int k) {
        TrieNode node = navigateToPrefix(prefix.toLowerCase());
        if (node == null) {
            return Collections.emptyList();
        }

        List<SearchTerm> results = new ArrayList<>();
        collectTerms(node, prefix.toLowerCase(), results);

        results.sort((a, b) -> Long.compare(b.getFrequency(), a.getFrequency()));

        if (results.size() > k) {
            return results.subList(0, k);
        }
        return results;
    }

    private TrieNode navigateToPrefix(String prefix) {
        TrieNode current = root;
        for (char c : prefix.toCharArray()) {
            TrieNode next = current.getChildren().get(c);
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    private void collectTerms(TrieNode node, String currentPrefix, List<SearchTerm> results) {
        if (node.isEndOfWord()) {
            results.add(new SearchTerm(currentPrefix, node.getFrequency()));
        }
        for (Map.Entry<Character, TrieNode> entry : node.getChildren().entrySet()) {
            collectTerms(entry.getValue(), currentPrefix + entry.getKey(), results);
        }
    }
}

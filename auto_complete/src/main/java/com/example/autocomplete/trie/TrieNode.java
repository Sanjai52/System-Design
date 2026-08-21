package com.example.autocomplete.trie;

import java.util.HashMap;
import java.util.Map;

public class TrieNode {
    private final Map<Character, TrieNode> children;
    private boolean endOfWord;
    private long frequency;

    public TrieNode() {
        this.children = new HashMap<>();
        this.endOfWord = false;
        this.frequency = 0;
    }

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public boolean isEndOfWord() {
        return endOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        this.endOfWord = endOfWord;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }
}

package com.crawler.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TokenizerTest {

    @Test
    void lowercasesAndCountsFrequencies() {
        Map<String, Integer> tf = TextTokenizer.tokenize("Hello, World! Hello");
        assertEquals(Map.of("hello", 2, "world", 1), tf);
    }

    @Test
    void dropsStopWordsAndShortTokens() {
        Map<String, Integer> tf = TextTokenizer.tokenize("The cat is on the mat a");
        assertEquals(Map.of("cat", 1, "mat", 1), tf);
    }

    @Test
    void nullAndBlankYieldEmpty() {
        assertTrue(TextTokenizer.tokenize(null).isEmpty());
        assertTrue(TextTokenizer.tokenize("   ").isEmpty());
    }

    @Test
    void capsTokensPerPage() {
        String big = "wordx ".repeat(12005);
        Map<String, Integer> tf = TextTokenizer.tokenize(big);
        int total = tf.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total <= 10000, "total=" + total);
    }
}

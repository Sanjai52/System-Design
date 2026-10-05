package com.crawler.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PageRankServiceTest {

    @Test
    void symmetricCycleSplitsRankEvenly() {
        Map<String, Set<String>> graph = Map.of(
                "A", Set.of("B"),
                "B", Set.of("C"),
                "C", Set.of("A"));

        Map<String, Double> ranks = PageRankService.computeRanks(graph);

        assertEquals(1.0 / 3, ranks.get("A"), 1e-9);
        assertEquals(1.0 / 3, ranks.get("B"), 1e-9);
        assertEquals(1.0 / 3, ranks.get("C"), 1e-9);
    }

    @Test
    void chainRanksLaterNodesHigher() {
        Map<String, Set<String>> graph = Map.of(
                "A", Set.of("B"),
                "B", Set.of("C"),
                "C", Set.of());

        Map<String, Double> ranks = PageRankService.computeRanks(graph);

        assertTrue(ranks.get("C") > ranks.get("B"), "C=" + ranks.get("C") + " B=" + ranks.get("B"));
        assertTrue(ranks.get("B") > ranks.get("A"), "B=" + ranks.get("B") + " A=" + ranks.get("A"));
    }

    @Test
    void singleDanglingNodeKeepsRankOne() {
        Map<String, Double> ranks = PageRankService.computeRanks(Map.of("A", Set.of()));

        assertEquals(1.0, ranks.get("A"), 1e-9);
    }

    @Test
    void emptyGraphYieldsEmpty() {
        assertTrue(PageRankService.computeRanks(Map.of()).isEmpty());
    }

    @Test
    void ranksSumToOne() {
        Map<String, Set<String>> graph = Map.of(
                "A", Set.of("B", "C"),
                "B", Set.of("C"),
                "C", Set.of("A"));

        Map<String, Double> ranks = PageRankService.computeRanks(graph);
        double sum = ranks.values().stream().mapToDouble(Double::doubleValue).sum();

        assertEquals(1.0, sum, 1e-6);
    }
}

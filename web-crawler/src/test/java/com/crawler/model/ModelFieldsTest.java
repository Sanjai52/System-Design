package com.crawler.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ModelFieldsTest {
    @Test
    void urlResultHoldsParentDepthAndChildren() {
        UrlResult r = new UrlResult("https://example.com/a", CrawlStatus.COMPLETED, 2);
        r.setParentUrl("https://example.com");
        r.setDepth(1);
        r.setChildUrls(List.of("https://example.com/b"));
        assertEquals("https://example.com", r.getParentUrl());
        assertEquals(1, r.getDepth());
        assertEquals(List.of("https://example.com/b"), r.getChildUrls());
    }

    @Test
    void crawlTaskHoldsParentAndDepth() {
        CrawlTask t = new CrawlTask("https://example.com/a", "https://example.com", 1);
        assertEquals("https://example.com/a", t.getUrl());
        assertEquals("https://example.com", t.getParentUrl());
        assertEquals(1, t.getDepth());
    }

    @Test
    void hierarchyNodeChildrenDefaultEmpty() {
        HierarchyNode n = new HierarchyNode("https://example.com", CrawlStatus.COMPLETED, 0, 2);
        assertNotNull(n.getChildren());
        assertTrue(n.getChildren().isEmpty());
        assertFalse(n.isAlreadyVisited());
    }
}

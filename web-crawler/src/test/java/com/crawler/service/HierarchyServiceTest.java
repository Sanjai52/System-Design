package com.crawler.service;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.HierarchyNode;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HierarchyServiceTest {

    @Mock
    CrawlStateRepository repo;

    @InjectMocks
    HierarchyService service;

    private static UrlResult r(String url, String parent, int depth, List<String> kids, CrawlStatus s) {
        UrlResult x = new UrlResult(url, s, kids.size());
        x.setParentUrl(parent);
        x.setDepth(depth);
        x.setChildUrls(kids);
        return x;
    }

    @Test
    void buildsNestedTreeWithDepths() {
        CrawlJob job = new CrawlJob("j1", "https://seed.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        when(repo.getUrlResults("j1")).thenReturn(List.of(
                r("https://seed.test", null, 0, List.of("https://seed.test/a"), CrawlStatus.COMPLETED),
                r("https://seed.test/a", "https://seed.test", 1, List.of(), CrawlStatus.COMPLETED)));

        HierarchyNode root = service.buildTree("j1", null);

        assertEquals("https://seed.test", root.getUrl());
        assertEquals(1, root.getChildren().size());
        assertEquals("https://seed.test/a", root.getChildren().get(0).getUrl());
        assertEquals(1, root.getChildren().get(0).getDepth());
    }

    @Test
    void cycleBecomesAlreadyVisitedLeaf() {
        CrawlJob job = new CrawlJob("j1", "https://s.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        when(repo.getUrlResults("j1")).thenReturn(List.of(
                r("https://s.test", null, 0, List.of("https://s.test/a"), CrawlStatus.COMPLETED),
                r("https://s.test/a", "https://s.test", 1, List.of("https://s.test"), CrawlStatus.COMPLETED),
                r("https://s.test", null, 0, List.of("https://s.test/a"), CrawlStatus.COMPLETED)));

        HierarchyNode root = service.buildTree("j1", null);

        HierarchyNode a = root.getChildren().get(0);
        assertEquals(1, a.getChildren().size());
        assertTrue(a.getChildren().get(0).isAlreadyVisited());
        assertTrue(a.getChildren().get(0).getChildren().isEmpty());
    }

    @Test
    void maxDepthPrunesChildren() {
        CrawlJob job = new CrawlJob("j1", "https://s.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        when(repo.getUrlResults("j1")).thenReturn(List.of(
                r("https://s.test", null, 0, List.of("https://s.test/a"), CrawlStatus.COMPLETED),
                r("https://s.test/a", "https://s.test", 1, List.of("https://s.test/b"), CrawlStatus.COMPLETED),
                r("https://s.test/b", "https://s.test/a", 2, List.of(), CrawlStatus.COMPLETED)));

        HierarchyNode root = service.buildTree("j1", 1);

        assertEquals(1, root.getChildren().size());
        assertTrue(root.getChildren().get(0).getChildren().isEmpty());
    }

    @Test
    void oldJobsWithoutParentsAttachToRoot() {
        CrawlJob job = new CrawlJob("j1", "https://s.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        UrlResult orphan = new UrlResult("https://s.test/z", CrawlStatus.COMPLETED, 0);
        UrlResult seed = new UrlResult("https://s.test", CrawlStatus.COMPLETED, 0);
        when(repo.getUrlResults("j1")).thenReturn(List.of(seed, orphan));

        HierarchyNode root = service.buildTree("j1", null);

        assertEquals(1, root.getChildren().size());
        assertEquals("https://s.test/z", root.getChildren().get(0).getUrl());
    }

    @Test
    void blankSearchQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.searchBySeed("  "));
    }
}

package com.crawler.service;

import com.crawler.model.ContentPage;
import com.crawler.model.DiscoveryHit;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContentDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ContentDiscoveryService.class);

    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 3;
    public static final int MAX_LINKS_PER_PAGE = 20;
    public static final int SNIPPET_RADIUS = 60;

    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;

    public ContentDiscoveryService(PageFetcherService pageFetcherService,
                                   UrlDiscoveryService urlDiscoveryService) {
        this.pageFetcherService = pageFetcherService;
        this.urlDiscoveryService = urlDiscoveryService;
    }

    public List<DiscoveryHit> discover(String url, String keyword, Integer depth) throws IOException {
        if (url == null || !urlDiscoveryService.isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Keyword must not be blank");
        }
        int maxDepth = depth == null ? MIN_DEPTH : Math.min(MAX_DEPTH, Math.max(MIN_DEPTH, depth));
        String needle = keyword.trim().toLowerCase();

        List<DiscoveryHit> hits = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[]{url, "0"});
        visited.add(url);

        while (!queue.isEmpty()) {
            String[] item = queue.poll();
            String current = item[0];
            int currentDepth = Integer.parseInt(item[1]);

            Document document;
            try {
                document = pageFetcherService.fetchPage(current);
            } catch (IOException e) {
                log.warn("Discovery fetch failed for {}: {}", current, e.getMessage());
                if (currentDepth == 0) throw e;
                continue;
            }

            String text = document.body() != null ? document.body().text() : "";
            int count = countMatches(text.toLowerCase(), needle);
            if (count > 0) {
                hits.add(new DiscoveryHit(current,
                        document.title() != null ? document.title() : "",
                        snippet(text, text.toLowerCase().indexOf(needle)),
                        count, currentDepth));
            }

            if (currentDepth < maxDepth) {
                List<String> links = urlDiscoveryService.extractLinks(document, current);
                int added = 0;
                for (String link : links) {
                    if (added >= MAX_LINKS_PER_PAGE) break;
                    if (visited.add(link)) {
                        queue.add(new String[]{link, String.valueOf(currentDepth + 1)});
                        added++;
                    }
                }
            }
        }
        return hits;
    }

    public ContentPage fetchContent(String url) throws IOException {
        if (url == null || !urlDiscoveryService.isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        Document document = pageFetcherService.fetchPage(url);
        String text = document.body() != null ? document.body().text() : "";
        return new ContentPage(url,
                document.title() != null ? document.title() : "", text);
    }

    static int countMatches(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(needle, from);
            if (idx < 0) break;
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    static String snippet(String text, int matchAt) {
        if (matchAt < 0 || text.isEmpty()) {
            return text.length() > 200 ? text.substring(0, 200) : text;
        }
        int start = Math.max(0, matchAt - SNIPPET_RADIUS);
        int end = Math.min(text.length(), matchAt + SNIPPET_RADIUS);
        String out = text.substring(start, end);
        if (start > 0) out = "…" + out;
        if (end < text.length()) out = out + "…";
        return out;
    }
}

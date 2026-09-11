package com.crawler.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
public class UrlDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(UrlDiscoveryService.class);

    public List<String> extractLinks(Document document, String baseUrl) {
        List<String> discoveredUrls = new ArrayList<>();
        Elements links = document.select("a[href]");

        for (Element link : links) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) {
                href = link.attr("href");
            }

            String absoluteUrl = resolveUrl(href, baseUrl);
            if (absoluteUrl != null && isValidUrl(absoluteUrl)) {
                discoveredUrls.add(absoluteUrl);
            }
        }

        log.debug("Discovered {} links from {}", discoveredUrls.size(), baseUrl);
        return discoveredUrls;
    }

    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;

        try {
            URL parsed = new URL(url);
            String protocol = parsed.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https")) return false;
            if (parsed.getHost() == null || parsed.getHost().isBlank()) return false;
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String resolveUrl(String href, String baseUrl) {
        if (href == null || href.isBlank()) return null;

        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }

        try {
            URL base = new URL(baseUrl);
            return new URL(base, href).toString();
        } catch (MalformedURLException e) {
            log.debug("Failed to resolve relative URL: {} against base {}", href, baseUrl);
            return null;
        }
    }
}

package com.crawler.service;

import com.crawler.config.CrawlerConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

@Service
public class PageFetcherService {

    private static final Logger log = LoggerFactory.getLogger(PageFetcherService.class);

    private final CrawlerConfig config;

    public PageFetcherService(CrawlerConfig config) {
        this.config = config;
    }

    public Document fetchPage(String url) throws IOException {
        log.debug("Fetching page: {}", url);
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(config.getConnectionTimeoutMs())
                    .followRedirects(true)
                    .maxBodySize(1024 * 1024)
                    .ignoreHttpErrors(false)
                    .get();
        } catch (SocketTimeoutException e) {
            log.warn("Timeout fetching URL: {}", url);
            throw new IOException("Connection timed out: " + url, e);
        } catch (UnknownHostException e) {
            log.warn("Unknown host: {}", url);
            throw new IOException("Unknown host: " + url, e);
        } catch (IOException e) {
            log.warn("IO error fetching URL: {} - {}", url, e.getMessage());
            throw e;
        }
    }
}

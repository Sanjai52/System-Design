package com.crawler.controller;

import com.crawler.service.ContentDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/discover")
public class DiscoveryController {

    private final ContentDiscoveryService discoveryService;

    public DiscoveryController(ContentDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @PostMapping
    public ResponseEntity<?> discover(@RequestBody Map<String, Object> body) {
        try {
            String url = (String) body.get("url");
            String keyword = (String) body.get("keyword");
            Integer depth = body.get("depth") instanceof Number n ? n.intValue() : null;
            return ResponseEntity.ok(discoveryService.discover(url, keyword, depth));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Fetch failed: " + e.getMessage()));
        }
    }

    @GetMapping("/content")
    public ResponseEntity<?> content(@RequestParam String url) {
        try {
            return ResponseEntity.ok(discoveryService.fetchContent(url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Fetch failed: " + e.getMessage()));
        }
    }
}

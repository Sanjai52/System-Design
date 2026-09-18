package com.crawler.controller;

import com.crawler.service.RankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final RankService rankService;

    public SearchController(RankService rankService) {
        this.rankService = rankService;
    }

    @GetMapping
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(required = false) Integer limit) {
        try {
            return ResponseEntity.ok(rankService.search(q, limit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

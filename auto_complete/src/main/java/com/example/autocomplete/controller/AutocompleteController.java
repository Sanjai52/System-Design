package com.example.autocomplete.controller;

import com.example.autocomplete.service.AutocompleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AutocompleteController {

    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<Map<String, Object>> autocomplete(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "10") int k) {

        AutocompleteService.AutocompleteResult result = autocompleteService.getSuggestions(prefix, k);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("prefix", prefix);
        response.put("suggestions", result.getSuggestions());
        response.put("count", result.getSuggestions().size());
        response.put("cached", result.isCached());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}

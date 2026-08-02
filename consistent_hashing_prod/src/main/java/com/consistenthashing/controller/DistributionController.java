package com.consistenthashing.controller;

import com.consistenthashing.dto.DistributionReport;
import com.consistenthashing.service.DistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/distribution")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;

    @GetMapping
    public DistributionReport report() {
        return distributionService.report();
    }

    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return distributionService.counts();
    }
}

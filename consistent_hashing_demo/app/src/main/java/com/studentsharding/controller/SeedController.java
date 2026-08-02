package com.studentsharding.controller;

import com.studentsharding.dto.Distribution;
import com.studentsharding.service.DistributionService;
import com.studentsharding.service.SeedService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seed")
public class SeedController {

    private final SeedService seedService;
    private final DistributionService distributionService;

    public SeedController(SeedService seedService, DistributionService distributionService) {
        this.seedService = seedService;
        this.distributionService = distributionService;
    }

    @PostMapping
    public Distribution seed(@RequestParam(defaultValue = "10000") int count) {
        seedService.seed(count);
        return distributionService.distribution();
    }
}

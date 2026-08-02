package com.studentsharding.controller;

import com.studentsharding.dto.Distribution;
import com.studentsharding.service.DistributionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/distribution")
public class DistributionController {

    private final DistributionService distributionService;

    public DistributionController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @GetMapping
    public Distribution distribution() {
        return distributionService.distribution();
    }
}

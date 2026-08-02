package com.studentsharding.controller;

import com.studentsharding.dto.RingInfo;
import com.studentsharding.service.RingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ring")
public class RingController {

    private final RingService ringService;

    public RingController(RingService ringService) {
        this.ringService = ringService;
    }

    @GetMapping
    public RingInfo ring() {
        return ringService.ringInfo();
    }
}

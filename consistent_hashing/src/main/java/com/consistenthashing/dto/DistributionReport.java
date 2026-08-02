package com.consistenthashing.dto;

import java.util.Map;

public record DistributionReport(
        Map<String, Long> counts,
        Map<String, Long> vnodes,
        long total,
        Map<String, Long> delta) {
}

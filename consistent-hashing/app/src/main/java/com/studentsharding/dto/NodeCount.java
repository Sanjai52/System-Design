package com.studentsharding.dto;

import java.util.List;

public record NodeCount(String nodeId, long count, double percentage) {

    public static NodeCount of(String nodeId, long count, long total) {
        return new NodeCount(nodeId, count, total == 0 ? 0 : Math.round(count * 10000.0 / total) / 100.0);
    }
}

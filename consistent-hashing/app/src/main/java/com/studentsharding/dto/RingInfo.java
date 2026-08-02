package com.studentsharding.dto;

import java.util.List;
import java.util.Map;

public record RingInfo(List<String> nodes, int virtualNodesPerNode, int totalVirtualPoints,
                       Map<String, Integer> perNodePoints, List<RingPoint> points) {
}

package com.consistenthashing.service;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.dto.DistributionReport;
import com.consistenthashing.storage.StudentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private final ConsistentHashRing ring;
    private final StudentStore store;

    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (StorageNode node : ring.getPhysicalNodes()) {
            counts.put(node.key(), store.countOn(node));
        }
        return counts;
    }

    public DistributionReport report() {
        return report(Map.of());
    }

    public DistributionReport report(Map<String, Long> before) {
        Map<String, Long> after = counts();
        long total = after.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Long> vnodes = new LinkedHashMap<>();
        ring.vnodeCounts().forEach((k, v) -> vnodes.put(k, v.longValue()));

        Map<String, Long> delta = new LinkedHashMap<>();
        for (String k : after.keySet()) {
            delta.put(k, after.get(k) - before.getOrDefault(k, 0L));
        }
        return new DistributionReport(after, vnodes, total, delta);
    }
}

package com.studentsharding.service;

import com.studentsharding.dto.Distribution;
import com.studentsharding.dto.NodeCount;
import com.studentsharding.model.Student;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DistributionService {

    private final NodeRegistryService registry;

    public DistributionService(NodeRegistryService registry) {
        this.registry = registry;
    }

    public Distribution distribution() {
        List<String> nodeIds = registry.ring().physicalNodes().stream().sorted().toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            long count = registry.template(nodeId)
                    .count(new Query(), Student.class, registry.collection());
            counts.put(nodeId, count);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<NodeCount> nodes = counts.entrySet().stream()
                .map(e -> NodeCount.of(e.getKey(), e.getValue(), total))
                .toList();
        return new Distribution(total, nodes);
    }
}

package com.studentsharding.service;

import com.studentsharding.dto.RingInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class RingService {

    private final NodeRegistryService registry;

    public RingService(NodeRegistryService registry) {
        this.registry = registry;
    }

    public RingInfo ringInfo() {
        return new RingInfo(
                new ArrayList<>(registry.ring().physicalNodes()),
                registry.ring().virtualNodesPerNode(),
                registry.ring().virtualPointCount(),
                registry.ring().virtualNodesPerNodeCounts(),
                registry.ring().points());
    }
}

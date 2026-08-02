package com.consistenthashing.config;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.consistenthash.HashFunction;
import com.consistenthashing.consistenthash.StorageNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ConsistentHashingConfig {

    @Value("${app.nodes}")
    private String nodesCsv;

    @Value("${app.virtual-nodes:150}")
    private int virtualNodes;

    @Bean
    public HashFunction hashFunction() {
        return new HashFunction();
    }

    @Bean
    public ConsistentHashRing consistentHashRing(HashFunction hashFunction) {
        ConsistentHashRing ring = new ConsistentHashRing(hashFunction, virtualNodes);
        for (String spec : nodesCsv.split(",")) {
            String[] parts = spec.split(":");
            StorageNode node = new StorageNode(parts[0], Integer.parseInt(parts[1]));
            ring.addNode(node);
            log.info("Registered initial storage node {}", node.key());
        }
        log.info("Hash ring ready with {} physical node(s), {} vnodes/node",
                ring.getPhysicalNodes().size(), virtualNodes);
        return ring;
    }
}

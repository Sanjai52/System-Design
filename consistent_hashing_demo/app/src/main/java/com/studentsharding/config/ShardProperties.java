package com.studentsharding.config;

import com.studentsharding.domain.ShardNode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "sharding")
public record ShardProperties(String database, String collection, int virtualNodes,
                              String containerPrefix, String composeFile, List<ShardNode> nodes) {
}

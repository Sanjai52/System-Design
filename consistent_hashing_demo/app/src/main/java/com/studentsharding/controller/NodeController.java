package com.studentsharding.controller;

import com.studentsharding.domain.ShardNode;
import com.studentsharding.dto.MigrationResponse;
import com.studentsharding.dto.NodeAddRequest;
import com.studentsharding.service.MigrationService;
import com.studentsharding.service.NodeLifecycleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final MigrationService migrationService;
    private final NodeLifecycleService nodeLifecycle;

    public NodeController(MigrationService migrationService, NodeLifecycleService nodeLifecycle) {
        this.migrationService = migrationService;
        this.nodeLifecycle = nodeLifecycle;
    }

    @PostMapping
    public MigrationResponse addNode(@Valid @RequestBody NodeAddRequest request) {
        ShardNode node = new ShardNode(request.id(), request.host(), request.port());
        nodeLifecycle.start(node);
        return migrationService.addNode(node);
    }

    @DeleteMapping("/{nodeId}")
    public MigrationResponse removeNode(@PathVariable String nodeId) {
        MigrationResponse response = migrationService.removeNode(nodeId);
        nodeLifecycle.stop(nodeId);
        return response;
    }

    @PostMapping("/{nodeId}/start")
    public Map<String, String> startNode(@PathVariable String nodeId,
                                         @RequestParam String host, @RequestParam int port) {
        nodeLifecycle.start(new ShardNode(nodeId, host, port));
        return Map.of("status", "started");
    }

    @PostMapping("/{nodeId}/stop")
    public Map<String, String> stopNode(@PathVariable String nodeId) {
        nodeLifecycle.stop(nodeId);
        return Map.of("status", "stopped");
    }
}

package com.studentsharding.controller;

import com.studentsharding.config.ShardProperties;
import com.studentsharding.dto.Distribution;
import com.studentsharding.dto.MigrationResponse;
import com.studentsharding.dto.NodeAddRequest;
import com.studentsharding.dto.RingInfo;
import com.studentsharding.service.MigrationService;
import com.studentsharding.service.NodeLifecycleService;
import com.studentsharding.service.StudentService;
import com.studentsharding.sharding.ShardManager;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ShardManager shardManager;
    private final MigrationService migrationService;
    private final StudentService studentService;
    private final NodeLifecycleService nodeLifecycle;

    public AdminController(ShardManager shardManager, MigrationService migrationService,
                           StudentService studentService, NodeLifecycleService nodeLifecycle) {
        this.shardManager = shardManager;
        this.migrationService = migrationService;
        this.studentService = studentService;
        this.nodeLifecycle = nodeLifecycle;
    }

    @GetMapping("/distribution")
    public Distribution distribution() {
        return studentService.distribution();
    }

    @GetMapping("/ring")
    public RingInfo ring() {
        return new RingInfo(
                new ArrayList<>(shardManager.ring().physicalNodes()),
                100,
                shardManager.ring().virtualPointCount(),
                shardManager.ring().virtualNodesPerNode(),
                shardManager.ring().points());
    }

    @PostMapping("/nodes")
    public MigrationResponse addNode(@Valid @RequestBody NodeAddRequest request) {
        ShardProperties.ShardNode node = new ShardProperties.ShardNode(request.id(), request.host(), request.port());
        nodeLifecycle.start(node);
        return migrationService.addNode(node);
    }

    @DeleteMapping("/nodes/{nodeId}")
    public MigrationResponse removeNode(@PathVariable String nodeId) {
        MigrationResponse response = migrationService.removeNode(nodeId);
        nodeLifecycle.stop(nodeId);
        return response;
    }

    @PostMapping("/nodes/{nodeId}/start")
    public Map<String, String> startNode(@PathVariable String nodeId, @RequestParam String host,
                                         @RequestParam int port) {
        nodeLifecycle.start(new ShardProperties.ShardNode(nodeId, host, port));
        return Map.of("status", "started");
    }

    @PostMapping("/nodes/{nodeId}/stop")
    public Map<String, String> stopNode(@PathVariable String nodeId) {
        nodeLifecycle.stop(nodeId);
        return Map.of("status", "stopped");
    }

    @PostMapping("/seed")
    public Distribution seed(@RequestParam(defaultValue = "10000") int count) {
        return studentService.seed(count);
    }
}

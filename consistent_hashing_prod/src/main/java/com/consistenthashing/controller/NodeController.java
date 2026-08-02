package com.consistenthashing.controller;

import com.consistenthashing.dto.CreateNodeRequest;
import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.service.NodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping
    public List<StorageNode> listNodes() {
        return nodeService.listNodes();
    }

    @GetMapping("/ring")
    public Map<String, Integer> vnodeCounts() {
        return nodeService.vnodeCounts();
    }

    @PostMapping
    public ResponseEntity<NodeService.NodeChange> addNode(@Valid @RequestBody CreateNodeRequest request) {
        NodeService.NodeChange change = nodeService.addNode(request.getHost(), request.getPort());
        return ResponseEntity.ok(change);
    }

    @DeleteMapping("/{host}/{port}")
    public ResponseEntity<NodeService.NodeChange> removeNode(@PathVariable String host, @PathVariable int port) {
        NodeService.NodeChange change = nodeService.removeNode(host, port);
        return ResponseEntity.ok(change);
    }
}

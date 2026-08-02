package com.consistenthashing.service;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;
import com.consistenthashing.storage.StudentStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    private final ConsistentHashRing ring;
    private final StudentStore store;

    public record NodeChange(String nodeKey, int migrated, Map<String, Long> before, Map<String, Long> after) {
    }

    public List<StorageNode> listNodes() {
        return ring.getPhysicalNodes();
    }

    public Map<String, Integer> vnodeCounts() {
        return ring.vnodeCounts();
    }

    public NodeChange addNode(String host, int port) {
        StorageNode node = new StorageNode(host, port);
        if (ring.getPhysicalNodes().contains(node)) {
            throw new IllegalArgumentException("node already registered: " + node.key());
        }
        Map<String, Long> before = snapshot();
        ring.addNode(node);
        log.info("Added node {} to the hash ring", node.key());

        int migrated = migrateOffExistingNodes(List.copyOf(ring.getPhysicalNodes()), node);
        Map<String, Long> after = snapshot();
        log.info("Add-node migration complete: {} record(s) moved to {}", migrated, node.key());
        return new NodeChange(node.key(), migrated, before, after);
    }

    public NodeChange removeNode(String host, int port) {
        StorageNode node = new StorageNode(host, port);
        Map<String, Long> before = snapshot();

        List<Student> stranded = store.allStudentsOn(node);
        ring.removeNode(node);
        log.info("Removed node {} from the hash ring; relocating {} record(s)", node.key(), stranded.size());

        int migrated = 0;
        for (Student s : stranded) {
            StorageNode newOwner = ring.getNode(s.getRollNo().toString());
            store.insertOn(s, newOwner);
            migrated++;
        }
        store.dropOn(node);

        Map<String, Long> after = snapshot();
        log.info("Remove-node migration complete: {} record(s) relocated", migrated);
        return new NodeChange(node.key(), migrated, before, after);
    }

    private int migrateOffExistingNodes(List<StorageNode> nodes, StorageNode newNode) {
        int migrated = 0;
        for (StorageNode existing : nodes) {
            if (existing.equals(newNode)) {
                continue;
            }
            for (Student s : store.allStudentsOn(existing)) {
                StorageNode newOwner = ring.getNode(s.getRollNo().toString());
                if (!newOwner.equals(existing)) {
                    store.insertOn(s, newOwner);
                    store.deleteOn(s.getRollNo(), existing);
                    migrated++;
                }
            }
        }
        return migrated;
    }

    private Map<String, Long> snapshot() {
        Map<String, Long> snap = new LinkedHashMap<>();
        for (StorageNode n : ring.getPhysicalNodes()) {
            snap.put(n.key(), store.countOn(n));
        }
        return snap;
    }
}

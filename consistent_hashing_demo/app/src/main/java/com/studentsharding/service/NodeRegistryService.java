package com.studentsharding.service;

import com.studentsharding.config.ShardProperties;
import com.studentsharding.domain.HashRing;
import com.studentsharding.domain.ShardNode;
import com.studentsharding.storage.MongoConnectionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NodeRegistryService {

    private final ShardProperties props;
    private final MongoConnectionManager connections;
    private final HashRing ring;
    private final Map<String, ShardNode> nodes = new ConcurrentHashMap<>();

    public NodeRegistryService(ShardProperties props, MongoConnectionManager connections) {
        this.props = props;
        this.connections = connections;
        this.ring = new HashRing(props.virtualNodes());
        props.nodes().forEach(this::register);
    }

    public synchronized void register(ShardNode node) {
        if (nodes.containsKey(node.id())) {
            return;
        }
        connections.connect(node);
        nodes.put(node.id(), node);
        ring.addNode(node.id());
    }

    public synchronized void removeFromRing(String nodeId) {
        nodes.remove(nodeId);
        ring.removeNode(nodeId);
    }

    public synchronized void close(String nodeId) {
        connections.disconnect(nodeId);
    }

    public String ownerOf(Integer rollNo) {
        return ring.findNode(HashRing.hash("student:" + rollNo));
    }

    public MongoTemplate template(String nodeId) {
        return connections.template(nodeId);
    }

    public boolean isRegistered(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public HashRing ring() {
        return ring;
    }

    public String collection() {
        return props.collection();
    }
}

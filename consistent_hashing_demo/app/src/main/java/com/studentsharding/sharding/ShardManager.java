package com.studentsharding.sharding;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.studentsharding.config.ShardProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShardManager {

    private final ShardProperties props;
    private final HashRing ring;
    private final Map<String, MongoTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, MongoClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ShardProperties.ShardNode> nodes = new ConcurrentHashMap<>();

    public ShardManager(ShardProperties props) {
        this.props = props;
        this.ring = new HashRing(props.virtualNodes());
        props.nodes().forEach(this::register);
    }

    public synchronized void register(ShardProperties.ShardNode node) {
        if (nodes.containsKey(node.id())) {
            return;
        }
        MongoClient client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://" + node.host() + ":" + node.port()))
                .build());
        clients.put(node.id(), client);
        templates.put(node.id(), new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, props.database())));
        nodes.put(node.id(), node);
        ring.addNode(node.id());
    }

    public synchronized void removeFromRing(String nodeId) {
        ring.removeNode(nodeId);
        nodes.remove(nodeId);
    }

    public synchronized void close(String nodeId) {
        templates.remove(nodeId);
        MongoClient client = clients.remove(nodeId);
        if (client != null) {
            client.close();
        }
    }

    public String nodeFor(Integer rollNo) {
        return ring.findNode(HashRing.hash("student:" + rollNo));
    }

    public MongoTemplate template(String nodeId) {
        return templates.get(nodeId);
    }

    public boolean isRegistered(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public HashRing ring() {
        return ring;
    }

    public Map<String, ShardProperties.ShardNode> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public String collection() {
        return props.collection();
    }
}

package com.studentsharding.storage;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.studentsharding.config.ShardProperties;
import com.studentsharding.domain.ShardNode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MongoConnectionManager {

    private final String database;
    private final Map<String, MongoTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, MongoClient> clients = new ConcurrentHashMap<>();

    public MongoConnectionManager(ShardProperties props) {
        this.database = props.database();
    }

    public void connect(ShardNode node) {
        if (clients.containsKey(node.id())) {
            return;
        }
        MongoClient client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://" + node.host() + ":" + node.port()))
                .build());
        clients.put(node.id(), client);
        templates.put(node.id(), new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, database)));
    }

    public void disconnect(String nodeId) {
        templates.remove(nodeId);
        MongoClient client = clients.remove(nodeId);
        if (client != null) {
            client.close();
        }
    }

    public MongoTemplate template(String nodeId) {
        return templates.get(nodeId);
    }
}

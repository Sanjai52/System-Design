package com.consistenthashing.storage;

import com.consistenthashing.consistenthash.StorageNode;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MongoClientProvider {

    private final ConcurrentMap<String, MongoClient> clients = new ConcurrentHashMap<>();

    public MongoClient get(StorageNode node) {
        return clients.computeIfAbsent(node.key(), k -> {
            String connectionString = "mongodb://" + node.key() + "/?connectTimeoutMS=3000&serverSelectionTimeoutMS=3000";
            return MongoClients.create(connectionString);
        });
    }

    public MongoCollection<Document> studentCollection(StorageNode node) {
        return get(node).getDatabase("College").getCollection("Student");
    }

    public void closeAll() {
        clients.values().forEach(MongoClient::close);
    }
}

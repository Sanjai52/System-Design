package com.studentsharding.service;

import com.studentsharding.config.ShardProperties;
import com.studentsharding.dto.Distribution;
import com.studentsharding.dto.MigrationResponse;
import com.studentsharding.model.Student;
import com.studentsharding.sharding.ShardManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MigrationService {

    private final ShardManager shardManager;
    private final StudentService studentService;

    public MigrationService(ShardManager shardManager, StudentService studentService) {
        this.shardManager = shardManager;
        this.studentService = studentService;
    }

    public MigrationResponse addNode(ShardProperties.ShardNode node) {
        if (shardManager.isRegistered(node.id())) {
            throw new IllegalArgumentException("Node " + node.id() + " is already on the ring");
        }
        shardManager.register(node);
        long moved = 0;
        List<String> snapshot = new ArrayList<>(shardManager.ring().physicalNodes());
        for (String srcId : snapshot) {
            for (Student student : shardManager.template(srcId).findAll(Student.class, shardManager.collection())) {
                String dstId = shardManager.nodeFor(student.getRollNo());
                if (!dstId.equals(srcId)) {
                    shardManager.template(dstId).save(student, shardManager.collection());
                    shardManager.template(srcId).remove(
                            Query.query(Criteria.where("_id").is(student.getRollNo())), shardManager.collection());
                    moved++;
                }
            }
        }
        return new MigrationResponse("ADD", node.id(), moved, studentService.distribution());
    }

    public MigrationResponse removeNode(String nodeId) {
        if (!shardManager.isRegistered(nodeId)) {
            throw new IllegalArgumentException("Node " + nodeId + " is not on the ring");
        }
        List<Student> orphans = new ArrayList<>(
                shardManager.template(nodeId).findAll(Student.class, shardManager.collection()));
        MongoTemplate removedTemplate = shardManager.template(nodeId);
        shardManager.removeFromRing(nodeId);
        for (Student student : orphans) {
            String owner = shardManager.nodeFor(student.getRollNo());
            shardManager.template(owner).save(student, shardManager.collection());
            removedTemplate.remove(
                    Query.query(Criteria.where("_id").is(student.getRollNo())), shardManager.collection());
        }
        shardManager.close(nodeId);
        return new MigrationResponse("REMOVE", nodeId, orphans.size(), studentService.distribution());
    }
}

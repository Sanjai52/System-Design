package com.studentsharding.service;

import com.studentsharding.domain.ShardNode;
import com.studentsharding.dto.Distribution;
import com.studentsharding.dto.MigrationResponse;
import com.studentsharding.model.Student;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MigrationService {

    private final NodeRegistryService registry;
    private final DistributionService distributionService;

    public MigrationService(NodeRegistryService registry, DistributionService distributionService) {
        this.registry = registry;
        this.distributionService = distributionService;
    }

    public MigrationResponse addNode(ShardNode node) {
        if (registry.isRegistered(node.id())) {
            throw new IllegalArgumentException("Node " + node.id() + " is already on the ring");
        }
        registry.register(node);
        long moved = 0;
        List<String> snapshot = new ArrayList<>(registry.ring().physicalNodes());
        for (String srcId : snapshot) {
            for (Student student : registry.template(srcId).findAll(Student.class, registry.collection())) {
                String dstId = registry.ownerOf(student.getRollNo());
                if (!dstId.equals(srcId)) {
                    registry.template(dstId).save(student, registry.collection());
                    registry.template(srcId).remove(
                            Query.query(Criteria.where("_id").is(student.getRollNo())), registry.collection());
                    moved++;
                }
            }
        }
        return new MigrationResponse("ADD", node.id(), moved, distributionService.distribution());
    }

    public MigrationResponse removeNode(String nodeId) {
        if (!registry.isRegistered(nodeId)) {
            throw new IllegalArgumentException("Node " + nodeId + " is not on the ring");
        }
        List<Student> orphans = new ArrayList<>(
                registry.template(nodeId).findAll(Student.class, registry.collection()));
        MongoTemplate removedTemplate = registry.template(nodeId);
        registry.removeFromRing(nodeId);
        for (Student student : orphans) {
            String owner = registry.ownerOf(student.getRollNo());
            registry.template(owner).save(student, registry.collection());
            removedTemplate.remove(
                    Query.query(Criteria.where("_id").is(student.getRollNo())), registry.collection());
        }
        registry.close(nodeId);
        return new MigrationResponse("REMOVE", nodeId, orphans.size(), distributionService.distribution());
    }
}

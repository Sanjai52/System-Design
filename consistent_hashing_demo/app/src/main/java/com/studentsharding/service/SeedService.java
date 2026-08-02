package com.studentsharding.service;

import com.studentsharding.model.Student;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeedService {

    private static final String[] DEPTS = {"CS", "EC", "ME", "IT"};

    private final NodeRegistryService registry;

    public SeedService(NodeRegistryService registry) {
        this.registry = registry;
    }

    public void seed(int count) {
        for (String nodeId : registry.ring().physicalNodes()) {
            registry.template(nodeId).remove(new Query(), registry.collection());
        }
        Map<String, List<Student>> batches = new LinkedHashMap<>();
        for (int i = 1; i <= count; i++) {
            String nodeId = registry.ownerOf(i);
            batches.computeIfAbsent(nodeId, k -> new ArrayList<>())
                    .add(new Student(i, "Student" + i, DEPTS[i % 4], (i % 4) + 1));
        }
        batches.forEach((nodeId, students) -> {
            MongoTemplate template = registry.template(nodeId);
            for (int i = 0; i < students.size(); i += 500) {
                template.insert(students.subList(i, Math.min(i + 500, students.size())), registry.collection());
            }
        });
    }
}

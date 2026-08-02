package com.studentsharding.service;

import com.studentsharding.dto.Distribution;
import com.studentsharding.dto.NodeCount;
import com.studentsharding.dto.StudentResponse;
import com.studentsharding.exception.StudentNotFoundException;
import com.studentsharding.model.Student;
import com.studentsharding.sharding.ShardManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentService {

    private static final String[] DEPTS = {"CS", "EC", "ME", "IT"};

    private final ShardManager shardManager;

    public StudentService(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    public StudentResponse save(Student student) {
        String nodeId = shardManager.nodeFor(student.getRollNo());
        shardManager.template(nodeId).save(student, shardManager.collection());
        return StudentResponse.of(student, nodeId);
    }

    public StudentResponse findByRollNo(Integer rollNo) {
        String nodeId = shardManager.nodeFor(rollNo);
        Student student = shardManager.template(nodeId)
                .findOne(Query.query(Criteria.where("_id").is(rollNo)), Student.class, shardManager.collection());
        if (student == null) {
            throw new StudentNotFoundException(rollNo);
        }
        return StudentResponse.of(student, nodeId);
    }

    public List<StudentResponse> findAll() {
        List<StudentResponse> result = new ArrayList<>();
        List<String> nodeIds = new ArrayList<>(shardManager.ring().physicalNodes());
        for (String nodeId : nodeIds) {
            shardManager.template(nodeId)
                    .findAll(Student.class, shardManager.collection())
                    .forEach(s -> result.add(StudentResponse.of(s, nodeId)));
        }
        return result;
    }

    public Distribution distribution() {
        List<String> nodeIds = new ArrayList<>(shardManager.ring().physicalNodes());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            long count = shardManager.template(nodeId)
                    .count(new Query(), Student.class, shardManager.collection());
            counts.put(nodeId, count);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<NodeCount> nodes = counts.entrySet().stream()
                .map(e -> NodeCount.of(e.getKey(), e.getValue(), total))
                .toList();
        return new Distribution(total, nodes);
    }

    public Distribution seed(int count) {
        List<String> nodeIds = new ArrayList<>(shardManager.ring().physicalNodes());
        for (String nodeId : nodeIds) {
            shardManager.template(nodeId).remove(new Query(), shardManager.collection());
        }
        Map<String, List<Student>> batches = new LinkedHashMap<>();
        for (int i = 1; i <= count; i++) {
            String nodeId = shardManager.nodeFor(i);
            batches.computeIfAbsent(nodeId, k -> new ArrayList<>())
                    .add(new Student(i, "Student" + i, DEPTS[i % 4], (i % 4) + 1));
        }
        batches.forEach((nodeId, students) -> {
            MongoTemplate template = shardManager.template(nodeId);
            for (int i = 0; i < students.size(); i += 500) {
                template.insert(students.subList(i, Math.min(i + 500, students.size())), shardManager.collection());
            }
        });
        return distribution();
    }
}

package com.studentsharding.service;

import com.studentsharding.dto.StudentResponse;
import com.studentsharding.exception.StudentNotFoundException;
import com.studentsharding.model.Student;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StudentService {

    private final NodeRegistryService registry;

    public StudentService(NodeRegistryService registry) {
        this.registry = registry;
    }

    public StudentResponse save(Student student) {
        String nodeId = registry.ownerOf(student.getRollNo());
        registry.template(nodeId).save(student, registry.collection());
        return StudentResponse.of(student, nodeId);
    }

    public StudentResponse findByRollNo(Integer rollNo) {
        String nodeId = registry.ownerOf(rollNo);
        Student student = registry.template(nodeId)
                .findOne(Query.query(Criteria.where("_id").is(rollNo)), Student.class, registry.collection());
        if (student == null) {
            throw new StudentNotFoundException(rollNo);
        }
        return StudentResponse.of(student, nodeId);
    }

    public List<StudentResponse> findAll() {
        List<StudentResponse> result = new ArrayList<>();
        for (String nodeId : registry.ring().physicalNodes()) {
            registry.template(nodeId)
                    .findAll(Student.class, registry.collection())
                    .forEach(s -> result.add(StudentResponse.of(s, nodeId)));
        }
        return result;
    }
}

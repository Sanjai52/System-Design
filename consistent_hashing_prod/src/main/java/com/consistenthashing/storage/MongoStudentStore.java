package com.consistenthashing.storage;

import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MongoStudentStore implements StudentStore {

    private final MongoClientProvider mongo;

    @Override
    public List<Student> allStudentsOn(StorageNode node) {
        MongoCollection<Document> collection = mongo.studentCollection(node);
        List<Student> students = new ArrayList<>();
        collection.find().forEach(doc -> students.add(StudentMapper.fromDocument(doc)));
        return students;
    }

    @Override
    public Student findByRollNoOn(Long rollNo, StorageNode node) {
        Document doc = mongo.studentCollection(node)
                .find(Filters.eq("_id", rollNo))
                .first();
        return StudentMapper.fromDocument(doc);
    }

    @Override
    public void insertOn(Student student, StorageNode node) {
        mongo.studentCollection(node).insertOne(StudentMapper.toDocument(student));
    }

    @Override
    public void deleteOn(Long rollNo, StorageNode node) {
        mongo.studentCollection(node).deleteOne(Filters.eq("_id", rollNo));
    }

    @Override
    public long countOn(StorageNode node) {
        return mongo.studentCollection(node).countDocuments();
    }

    @Override
    public void dropOn(StorageNode node) {
        mongo.studentCollection(node).drop();
    }
}

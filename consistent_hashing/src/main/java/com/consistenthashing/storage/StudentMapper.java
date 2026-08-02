package com.consistenthashing.storage;

import com.consistenthashing.model.Student;
import org.bson.Document;

public final class StudentMapper {

    private StudentMapper() {
    }

    public static Document toDocument(Student student) {
        Document doc = new Document();
        doc.put("_id", student.getRollNo());
        doc.put("name", student.getName());
        doc.put("dept", student.getDept());
        doc.put("year", student.getYear());
        return doc;
    }

    public static Student fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        return Student.builder()
                .rollNo(doc.getLong("_id"))
                .name(doc.getString("name"))
                .dept(doc.getString("dept"))
                .year(doc.getInteger("year", 0))
                .build();
    }
}

package com.consistenthashing.storage;

import com.consistenthashing.model.Student;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentMapperTest {

    @Test
    void testRoundTrip() {
        Student student = Student.builder()
                .rollNo(1001L)
                .name("Alice")
                .dept("CS")
                .year(3)
                .build();

        Document doc = StudentMapper.toDocument(student);
        assertEquals(1001L, doc.getLong("_id"));
        assertEquals("Alice", doc.getString("name"));
        assertEquals("CS", doc.getString("dept"));
        assertEquals(3, doc.getInteger("year").intValue());

        Student back = StudentMapper.fromDocument(doc);
        assertEquals(student.getRollNo(), back.getRollNo());
        assertEquals(student.getName(), back.getName());
        assertEquals(student.getDept(), back.getDept());
        assertEquals(student.getYear(), back.getYear());
    }

    @Test
    void testFromNullDocument() {
        assertNull(StudentMapper.fromDocument(null));
    }
}

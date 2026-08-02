package com.studentsharding.dto;

import com.studentsharding.model.Student;

public record StudentResponse(Integer rollNo, String name, String dept, Integer year, String shard) {

    public static StudentResponse of(Student student, String shard) {
        return new StudentResponse(student.getRollNo(), student.getName(), student.getDept(), student.getYear(), shard);
    }
}

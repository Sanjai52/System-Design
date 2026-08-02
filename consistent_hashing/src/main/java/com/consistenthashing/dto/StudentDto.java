package com.consistenthashing.dto;

import com.consistenthashing.model.Student;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentDto {

    private Long rollNo;
    private String name;
    private String dept;
    private int year;

    public static StudentDto from(Student student) {
        return new StudentDto(student.getRollNo(), student.getName(), student.getDept(), student.getYear());
    }
}

package com.consistenthashing.dto;

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

    public static StudentDto from(Long rollNo, String name, String dept, int year) {
        return new StudentDto(rollNo, name, dept, year);
    }
}

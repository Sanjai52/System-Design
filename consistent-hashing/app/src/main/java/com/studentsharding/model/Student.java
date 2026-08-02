package com.studentsharding.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "Student")
public class Student {

    @Id
    private Integer rollNo;
    private String name;
    private String dept;
    private Integer year;

    public Student() {
    }

    public Student(Integer rollNo, String name, String dept, Integer year) {
        this.rollNo = rollNo;
        this.name = name;
        this.dept = dept;
        this.year = year;
    }

    public Integer getRollNo() {
        return rollNo;
    }

    public void setRollNo(Integer rollNo) {
        this.rollNo = rollNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}

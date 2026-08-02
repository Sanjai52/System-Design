package com.studentsharding.exception;

public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(Integer rollNo) {
        super("Student with rollNo " + rollNo + " not found");
    }
}

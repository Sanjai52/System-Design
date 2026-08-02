package com.studentsharding.controller;

import com.studentsharding.dto.StudentRequest;
import com.studentsharding.dto.StudentResponse;
import com.studentsharding.model.Student;
import com.studentsharding.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@Valid @RequestBody StudentRequest request) {
        return studentService.save(new Student(request.rollNo(), request.name(), request.dept(), request.year()));
    }

    @GetMapping("/{rollNo}")
    public StudentResponse get(@PathVariable Integer rollNo) {
        return studentService.findByRollNo(rollNo);
    }

    @GetMapping
    public List<StudentResponse> getAll() {
        return studentService.findAll();
    }
}

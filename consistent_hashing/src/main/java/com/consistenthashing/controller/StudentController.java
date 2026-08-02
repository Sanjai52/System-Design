package com.consistenthashing.controller;

import com.consistenthashing.dto.CreateStudentRequest;
import com.consistenthashing.dto.StudentDto;
import com.consistenthashing.exception.StudentNotFoundException;
import com.consistenthashing.model.Student;
import com.consistenthashing.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @PostMapping
    public ResponseEntity<StudentDto> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        Student student = studentService.createStudent(
                request.getRollNo(), request.getName(), request.getDept(), request.getYear());
        return ResponseEntity.status(HttpStatus.CREATED).body(StudentDto.from(student));
    }

    @GetMapping("/{rollNo}")
    public ResponseEntity<StudentDto> getStudent(@PathVariable Long rollNo) {
        Student student = studentService.getByRollNo(rollNo);
        if (student == null) {
            throw new StudentNotFoundException(rollNo);
        }
        return ResponseEntity.ok(StudentDto.from(student));
    }

    @GetMapping
    public List<StudentDto> listStudents() {
        return studentService.allStudents().stream()
                .map(StudentDto::from)
                .toList();
    }

    @DeleteMapping("/{rollNo}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long rollNo) {
        Student existing = studentService.getByRollNo(rollNo);
        if (existing == null) {
            throw new StudentNotFoundException(rollNo);
        }
        studentService.deleteStudent(rollNo);
        return ResponseEntity.noContent().build();
    }
}

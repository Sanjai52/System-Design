package com.consistenthashing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateStudentRequest {

    @NotNull
    private Long rollNo;

    @NotBlank
    private String name;

    @NotBlank
    private String dept;

    private int year;
}

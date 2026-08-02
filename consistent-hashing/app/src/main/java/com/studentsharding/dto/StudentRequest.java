package com.studentsharding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StudentRequest(
        @NotNull @Min(1) Integer rollNo,
        @NotBlank String name,
        @NotBlank String dept,
        @NotNull @Min(1) @Max(4) Integer year) {
}

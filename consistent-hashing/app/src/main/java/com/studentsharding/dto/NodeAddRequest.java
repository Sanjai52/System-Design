package com.studentsharding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record NodeAddRequest(
        @NotBlank String id,
        @NotBlank String host,
        @Positive int port) {
}

package io.github.kalayciburak.stackmate.sample.demo.validation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ValidationRequest(
        @NotBlank(message = "name must not be blank") String name,
        @Min(value = 1, message = "quantity must be at least 1") int quantity) {}

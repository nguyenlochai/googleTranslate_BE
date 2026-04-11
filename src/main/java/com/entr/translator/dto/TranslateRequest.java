package com.entr.translator.dto;

import jakarta.validation.constraints.NotBlank;

public record TranslateRequest(
        @NotBlank String text,
        @NotBlank String source,
        @NotBlank String target
) {}

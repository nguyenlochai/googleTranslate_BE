package com.entr.translator.dto;

import jakarta.validation.constraints.NotBlank;

public record TutorChatRequest(
    @NotBlank String message,
    String email
) {}

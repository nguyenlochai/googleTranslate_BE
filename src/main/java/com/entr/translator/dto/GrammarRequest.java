package com.entr.translator.dto;

import jakarta.validation.constraints.NotBlank;

public record GrammarRequest(@NotBlank String text) {}

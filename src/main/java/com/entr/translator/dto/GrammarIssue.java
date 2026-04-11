package com.entr.translator.dto;

public record GrammarIssue(String message, String shortMessage, String replacement, int offset, int length) {}

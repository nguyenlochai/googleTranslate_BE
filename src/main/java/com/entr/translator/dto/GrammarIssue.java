package com.entr.translator.dto;

/**
 * type: grammar | spelling | style | conciseness
 */
public record GrammarIssue(String type, String message, String shortMessage, String replacement, int offset, int length) {}

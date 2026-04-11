package com.entr.translator.dto;

import java.util.List;

public record GrammarResponse(
        int score,
        int issueCount,
        String correctedText,
        String improvedText,
        String vietnameseMeaning,
        List<GrammarIssue> issues
) {}

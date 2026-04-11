package com.entr.translator.dto;

import java.util.List;

public record DictionaryResponse(
        String word,
        String phonetic,
        List<String> meanings,
        List<String> examples,
        List<String> synonyms,
        List<String> antonyms,
        List<String> relatedWords,
        String sourceUrl,
        int masteryScore,
        int fluencyScore
) {}

package com.entr.translator.dto;

public record TranslateResponse(String translatedText, String sourceLanguage, String targetLanguage, String provider) {}

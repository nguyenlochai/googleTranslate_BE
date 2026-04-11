package com.entr.translator.dto;

import java.util.List;

public record TutorChatResponse(
    String response,
    List<String> suggestions
) {}

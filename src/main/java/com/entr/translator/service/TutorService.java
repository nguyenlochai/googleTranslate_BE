package com.entr.translator.service;

import com.entr.translator.dto.TutorChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class TutorService {
    private final TranslationService translationService;
    private final Random random = new Random();

    public TutorService(TranslationService translationService) {
        this.translationService = translationService;
    }

    public TutorChatResponse chat(String message, String userEmail) {
        String response;
        List<String> suggestions = new ArrayList<>();

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("hello") || lowerMessage.contains("hi")) {
            response = "Hello! I'm your FluidEnglish Tutor. I can help you with translations, grammar, or just practice conversation. What would you like to learn today?";
            suggestions.add("How do I say 'thank you' in Vietnamese?");
            suggestions.add("Check my grammar");
        } else if (lowerMessage.contains("translate") || lowerMessage.contains("how to say")) {
            // Extract what to translate if possible, or just respond
            response = "I can certainly help you translate! For more complex phrases, you can use the Tutor AI Translation tool directly. Is there a specific word you're curious about?";
            suggestions.add("Translate 'adventure'");
            suggestions.add("Explain 'serendipity'");
        } else if (lowerMessage.contains("grammar")) {
            response = "Grammar is the skeleton of language! You can use our Writing Lab for deep analysis, or just type a sentence here and I'll give you a quick check.";
            suggestions.add("Is 'I goes to school' correct?");
        } else {
            // Default: try to be helpful
            response = "That's interesting! As your tutor, I'd recommend practicing this context in a full sentence. Would you like me to translate this for you or explain its usage?";
            suggestions.add("Explain usage");
            suggestions.add("Translate to Vietnamese");
        }

        return new TutorChatResponse(response, suggestions);
    }
}

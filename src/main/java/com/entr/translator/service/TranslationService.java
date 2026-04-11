package com.entr.translator.service;

import com.entr.translator.dto.TranslateResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class TranslationService {
    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public TranslateResponse translate(String text, String source, String target) {
        if (text == null) return new TranslateResponse("", source, target, "MyMemory");
        String cleanText = text.trim();
        try {
            String url = "https://api.mymemory.translated.net/get?q=" +
                    URLEncoder.encode(cleanText, StandardCharsets.UTF_8) +
                    "&langpair=" + source + "|" + target;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                return new TranslateResponse("", source, target, "MyMemory");
            }

            Map<String, Object> responseData = (Map<String, Object>) response.get("responseData");
            String translatedText = responseData != null ? String.valueOf(responseData.getOrDefault("translatedText", "")) : "";
            
            // Decode potential URL encoded or HTML entities (MyMemory sometimes returns them)
            try {
                translatedText = URLDecoder.decode(translatedText, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            
            return new TranslateResponse(translatedText, source, target, "MyMemory");
        } catch (Exception e) {
            return new TranslateResponse("", source, target, "MyMemory");
        }
    }
}

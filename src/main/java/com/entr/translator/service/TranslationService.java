package com.entr.translator.service;

import com.entr.translator.dto.TranslateResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class TranslationService {
    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public TranslateResponse translate(String text, String source, String target) {
        if (text == null) return new TranslateResponse("", source, target, "MyMemory");

        String cleanText = text.trim();
        if (cleanText.isBlank()) return new TranslateResponse("", source, target, "MyMemory");

        // 1) Try MyMemory first
        try {
            String url = "https://api.mymemory.translated.net/get?q=" +
                    URLEncoder.encode(cleanText, StandardCharsets.UTF_8) +
                    "&langpair=" + source + "|" + target;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                Map<String, Object> responseData = (Map<String, Object>) response.get("responseData");
                String translatedText = responseData != null
                        ? String.valueOf(responseData.getOrDefault("translatedText", ""))
                        : "";

                translatedText = safeMultiDecode(translatedText);

                // If MyMemory top-level output is weak, try best candidate from `matches` list.
                if (!isUsableTranslation(cleanText, translatedText)) {
                    String bestFromMatches = extractBestMyMemoryMatch(response, cleanText);
                    if (isUsableTranslation(cleanText, bestFromMatches)) {
                        translatedText = bestFromMatches;
                    }
                }

                // If MyMemory gives a usable translation, return it.
                if (isUsableTranslation(cleanText, translatedText)) {
                    return new TranslateResponse(translatedText, source, target, "MyMemory");
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }

        // 2) Fallback to Google public endpoint (helps vi -> en and noisy cases)
        try {
            String fallback = translateWithGoogle(cleanText, source, target);
            if (isUsableTranslation(cleanText, fallback)) {
                return new TranslateResponse(fallback, source, target, "GoogleFallback");
            }
        } catch (Exception ignored) {
            // final fallback below
        }

        // 3) Final fallback: return original text instead of encoded garbage
        return new TranslateResponse(cleanText, source, target, "FallbackOriginal");
    }

    @SuppressWarnings("unchecked")
    private String translateWithGoogle(String text, String source, String target) {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
                source + "&tl=" + target + "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

        Object raw = restTemplate.getForObject(url, Object.class);
        if (!(raw instanceof List<?> outer) || outer.isEmpty()) return "";

        Object sentencesObj = outer.get(0);
        if (!(sentencesObj instanceof List<?> sentences)) return "";

        StringBuilder sb = new StringBuilder();
        for (Object s : sentences) {
            if (s instanceof List<?> row && !row.isEmpty() && row.get(0) != null) {
                sb.append(row.get(0).toString());
            }
        }

        return safeMultiDecode(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private String extractBestMyMemoryMatch(Map<String, Object> response, String sourceText) {
        Object matchesObj = response.get("matches");
        if (!(matchesObj instanceof List<?> matches) || matches.isEmpty()) return "";

        String best = "";
        double bestScore = -1.0;

        for (Object m : matches) {
            if (!(m instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> match = (Map<String, Object>) rawMap;

            Object translationObj = match.get("translation");
            if (translationObj == null) continue;

            String candidate = safeMultiDecode(String.valueOf(translationObj));
            if (!isUsableTranslation(sourceText, candidate)) continue;

            double score = toDouble(match.get("match"));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private double toDouble(Object v) {
        if (v == null) return -1.0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return -1.0;
        }
    }

    private String safeMultiDecode(String input) {
        if (input == null) return "";
        String out = input;

        // Decode up to 3 rounds for cases like s%25C3%25A1ch -> s%C3%A1ch -> sách
        for (int i = 0; i < 3; i++) {
            try {
                String decoded = URLDecoder.decode(out, StandardCharsets.UTF_8);
                if (decoded.equals(out)) break;
                out = decoded;
            } catch (Exception e) {
                break;
            }
        }

        return out.trim();
    }

    private boolean isUsableTranslation(String sourceText, String translatedText) {
        if (translatedText == null || translatedText.isBlank()) return false;

        String src = sourceText.trim();
        String dst = translatedText.trim();

        // reject unchanged responses (common provider failure)
        return !src.equalsIgnoreCase(dst);
    }
}

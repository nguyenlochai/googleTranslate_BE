package com.entr.translator.service;

import com.entr.translator.dto.GrammarIssue;
import com.entr.translator.dto.GrammarResponse;
import com.entr.translator.dto.TranslateResponse;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GrammarService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final TranslationService translationService;

    public GrammarService(TranslationService translationService) {
        this.translationService = translationService;
    }

    @SuppressWarnings("unchecked")
    public GrammarResponse analyze(String text) {
        try {
            String url = "https://api.languagetool.org/v2/check";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("text", text);
            form.add("language", "en-US");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> body = response.getBody();

            List<Map<String, Object>> matches = body == null ? List.of() : (List<Map<String, Object>>) body.getOrDefault("matches", List.of());
            List<GrammarIssue> issues = new ArrayList<>();

            // 1) Custom fixes for repeated trailing letters (e.g. fromssss -> from)
            issues.addAll(buildRepeatedLetterIssues(text));

            // 2) LanguageTool matches
            String corrected = text;
            for (Map<String, Object> match : matches) {
                String message = String.valueOf(match.getOrDefault("message", ""));
                String shortMessage = String.valueOf(match.getOrDefault("shortMessage", ""));
                int offset = ((Number) match.getOrDefault("offset", 0)).intValue();
                int length = ((Number) match.getOrDefault("length", 0)).intValue();

                String replacement = "";
                List<Map<String, Object>> replacements = (List<Map<String, Object>>) match.getOrDefault("replacements", List.of());
                if (!replacements.isEmpty()) {
                    replacement = String.valueOf(replacements.get(0).getOrDefault("value", ""));
                }

                String type = detectIssueType(match);
                issues.add(new GrammarIssue(type, message, shortMessage, replacement, offset, length));
            }

            corrected = applyCorrections(text, issues);
            String improved = improveText(corrected);
            TranslateResponse meaning = translationService.translate(corrected, "en", "vi");

            int score = Math.max(0, 100 - (issues.size() * 7));
            return new GrammarResponse(score, issues.size(), corrected, improved, meaning.translatedText(), issues);
        } catch (Exception e) {
            TranslateResponse meaning = translationService.translate(text, "en", "vi");
            return new GrammarResponse(70, 0, text, improveText(text), meaning.translatedText(), List.of());
        }
    }

    private String applyCorrections(String text, List<GrammarIssue> issues) {
        String result = text;
        List<GrammarIssue> reversed = new ArrayList<>(issues);
        reversed.sort((a, b) -> Integer.compare(b.offset(), a.offset()));

        for (GrammarIssue issue : reversed) {
            if (issue.replacement() == null || issue.replacement().isBlank()) continue;
            int start = Math.max(0, issue.offset());
            int end = Math.min(result.length(), start + issue.length());
            if (start <= end && start < result.length()) {
                result = result.substring(0, start) + issue.replacement() + result.substring(end);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String detectIssueType(Map<String, Object> match) {
        try {
            Map<String, Object> rule = (Map<String, Object>) match.getOrDefault("rule", Map.of());
            String issueType = String.valueOf(rule.getOrDefault("issueType", ""));
            if (issueType != null) issueType = issueType.toLowerCase();

            Map<String, Object> category = (Map<String, Object>) rule.getOrDefault("category", Map.of());
            String catId = String.valueOf(category.getOrDefault("id", ""));
            if (catId != null) catId = catId.toLowerCase();

            if ("misspelling".equals(issueType) || catId.contains("typos")) return "spelling";
            if (catId.contains("style")) return "style";
            return "grammar";
        } catch (Exception ignore) {
            return "grammar";
        }
    }

    private List<GrammarIssue> buildRepeatedLetterIssues(String text) {
        // Detect words ending with 3+ repeated letters: fromssss -> from
        // Offset/length are for the whole token so UI replacement is obvious.
        List<GrammarIssue> list = new ArrayList<>();
        if (text == null || text.isBlank()) return list;

        // word boundary, capture base + trailing repeats
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b([A-Za-z]{2,}?)([A-Za-z])\\2{2,}\\b");
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) {
            String base = m.group(1);
            String token = m.group(0);
            int offset = m.start();
            int length = token.length();

            // Only if base looks meaningful
            if (base != null && base.length() >= 2 && !base.equalsIgnoreCase(token)) {
                list.add(new GrammarIssue(
                        "spelling",
                        "Repeated letters detected",
                        "SPELLING",
                        base,
                        offset,
                        length
                ));
            }
        }
        return list;
    }

    private String improveText(String text) {
        String improved = text.trim();
        if (improved.isEmpty()) return improved;

        // Basic professional replacements
        improved = improved.replace("very good", "excellent")
                         .replace("very bad", "terrible")
                         .replace("get better", "improve")
                         .replace("thing", "aspect")
                         .replace("stuff", "information")
                         .replace("happy", "delighted")
                         .replace("sad", "disappointed");

        improved = improved.substring(0, 1).toUpperCase() + improved.substring(1);
        if (!improved.endsWith(".") && !improved.endsWith("!") && !improved.endsWith("?")) {
            improved += ".";
        }
        return improved.replaceAll("\\s+", " ");
    }
}

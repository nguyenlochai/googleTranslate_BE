package com.entr.translator.service;

import com.entr.translator.dto.TranslateResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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

        boolean enToVi = "en".equalsIgnoreCase(source) && "vi".equalsIgnoreCase(target);
        boolean viToEn = "vi".equalsIgnoreCase(source) && "en".equalsIgnoreCase(target);

        // EN -> VI: ưu tiên Google fallback để ra kết quả tự nhiên hơn.
        if (enToVi) {
            String g = tryGoogle(cleanText, source, target);
            if (isUsableTranslation(cleanText, g)) {
                return new TranslateResponse(g, source, target, "GoogleFallback");
            }

            String mm = tryMyMemory(cleanText, source, target);
            if (isUsableTranslation(cleanText, mm)) {
                return new TranslateResponse(mm, source, target, "MyMemory");
            }

            return new TranslateResponse(cleanText, source, target, "FallbackOriginal");
        }

        // VI -> EN: thêm nhánh riêng, ưu tiên Google text endpoint để tránh kết quả trộn Việt/Anh.
        if (viToEn) {
            String gDirect = tryGoogleViToEnDirect(cleanText);
            if (isUsableTranslation(cleanText, gDirect)) {
                return new TranslateResponse(gDirect, source, target, "GoogleViEnDirect");
            }

            String mm = tryMyMemory(cleanText, source, target);
            if (isUsableTranslation(cleanText, mm)) {
                return new TranslateResponse(mm, source, target, "MyMemory");
            }

            String g = tryGoogle(cleanText, source, target);
            if (isUsableTranslation(cleanText, g)) {
                return new TranslateResponse(g, source, target, "GoogleFallback");
            }

            String ascii = removeVietnameseDiacritics(cleanText);
            if (!ascii.equalsIgnoreCase(cleanText) && !ascii.isBlank()) {
                String gAscii = tryGoogleViToEnDirect(ascii);
                if (isUsableTranslation(ascii, gAscii)) {
                    return new TranslateResponse(gAscii, source, target, "GoogleViEnAsciiDirect");
                }

                String mmAscii = tryMyMemory(ascii, source, target);
                if (isUsableTranslation(ascii, mmAscii)) {
                    return new TranslateResponse(mmAscii, source, target, "MyMemoryAsciiFallback");
                }

                String gAsciiFallback = tryGoogle(ascii, source, target);
                if (isUsableTranslation(ascii, gAsciiFallback)) {
                    return new TranslateResponse(gAsciiFallback, source, target, "GoogleAsciiFallback");
                }
            }

            return new TranslateResponse(cleanText, source, target, "FallbackOriginal");
        }

        // Other language pairs
        String mm = tryMyMemory(cleanText, source, target);
        if (isUsableTranslation(cleanText, mm)) {
            return new TranslateResponse(mm, source, target, "MyMemory");
        }

        String g = tryGoogle(cleanText, source, target);
        if (isUsableTranslation(cleanText, g)) {
            return new TranslateResponse(g, source, target, "GoogleFallback");
        }

        return new TranslateResponse(cleanText, source, target, "FallbackOriginal");
    }

    @SuppressWarnings("unchecked")
    private String tryMyMemory(String text, String source, String target) {
        try {
            String url = "https://api.mymemory.translated.net/get?q=" +
                    URLEncoder.encode(text, StandardCharsets.UTF_8) +
                    "&langpair=" + source + "|" + target;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return "";

            Map<String, Object> responseData = (Map<String, Object>) response.get("responseData");
            String translatedText = responseData != null
                    ? String.valueOf(responseData.getOrDefault("translatedText", ""))
                    : "";

            translatedText = safeMultiDecode(translatedText);

            // Nếu top-level yếu, lấy bản tốt nhất từ matches
            if (!isUsableTranslation(text, translatedText)) {
                String bestFromMatches = extractBestMyMemoryMatch(response, text);
                if (isUsableTranslation(text, bestFromMatches)) {
                    translatedText = bestFromMatches;
                }
            }

            return translatedText;
        } catch (Exception ignored) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String tryGoogle(String text, String source, String target) {
        try {
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
        } catch (Exception ignored) {
            return "";
        }
    }

    private String tryGoogleViToEnDirect(String text) {
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=vi&tl=en&dt=t&q=" +
                    URLEncoder.encode(text, StandardCharsets.UTF_8);

            String raw = restTemplate.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) return "";

            // Response starts like: [[["let's go play","di choi ..."
            int firstQuote = raw.indexOf('"');
            if (firstQuote < 0) return "";
            int secondQuote = findStringEnd(raw, firstQuote + 1);
            if (secondQuote <= firstQuote) return "";

            String extracted = raw.substring(firstQuote + 1, secondQuote)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\u003c", "<")
                    .replace("\\u003e", ">")
                    .replace("\\u0026", "&");

            return safeMultiDecode(extracted);
        } catch (Exception ignored) {
            return "";
        }
    }

    private int findStringEnd(String s, int from) {
        boolean escaped = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') return i;
        }
        return -1;
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

    private String removeVietnameseDiacritics(String input) {
        String s = input.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private boolean isUsableTranslation(String sourceText, String translatedText) {
        if (translatedText == null || translatedText.isBlank()) return false;

        String src = sourceText.trim();
        String dst = translatedText.trim();

        // reject unchanged responses (common provider failure)
        return !src.equalsIgnoreCase(dst);
    }
}

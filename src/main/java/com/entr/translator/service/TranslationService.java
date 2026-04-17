package com.entr.translator.service;

import com.entr.translator.dto.TranslateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Service
public class TranslationService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.translate.google.api-key:}")
    private String googleApiKey;

    @Value("${app.translate.libre.enabled:true}")
    private boolean libreEnabled;

    @Value("${app.translate.libre.url:https://libretranslate.de/translate}")
    private String libreUrl;

    @Value("${app.translate.argos.enabled:true}")
    private boolean argosEnabled;

    @Value("${app.translate.argos.python-command:python}")
    private String argosPythonCommand;

    @Value("${app.translate.argos.script-path:tools/argos_translate.py}")
    private String argosScriptPath;

    // Small in-memory cache for local stability/perf when users retry same text.
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final int MAX_CHUNK_CHARS = 280;

    public TranslateResponse translate(String text, String source, String target) {
        if (text == null) return new TranslateResponse("", source, target, "LocalStable");

        String cleanText = text.trim();
        if (cleanText.isBlank()) return new TranslateResponse("", source, target, "LocalStable");

        String cacheKey = (source + "|" + target + "|" + cleanText).toLowerCase();
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return new TranslateResponse(cached, source, target, "LocalCache");
        }

        boolean viToEn = "vi".equalsIgnoreCase(source) && "en".equalsIgnoreCase(target);

        String translated;
        if (shouldSplit(cleanText)) {
            translated = translateLongTextBySentence(cleanText, source, target, viToEn);
        } else {
            translated = translateUnitRobust(cleanText, source, target, viToEn);
        }

        if (translated == null || translated.isBlank()) translated = cleanText;

        // Cache only decent outputs; avoid poisoning cache with low-quality fallback text.
        if (shouldCache(cleanText, translated, viToEn)) {
            cache.put(cacheKey, translated);
        }

        return new TranslateResponse(translated, source, target, "LocalStable");
    }

    private boolean shouldSplit(String text) {
        if (text.length() > MAX_CHUNK_CHARS) return true;
        return text.contains(".") || text.contains("?") || text.contains("!") || text.contains(";") || text.contains("\n") || text.contains(",");
    }

    private String translateLongTextBySentence(String text, String source, String target, boolean viToEn) {
        List<String> parts = splitIntoChunks(text, MAX_CHUNK_CHARS);
        if (parts.isEmpty()) {
            return translateUnitRobust(text, source, target, viToEn);
        }

        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String p = part == null ? "" : part.trim();
            if (p.isBlank()) continue;
            String unit = translateUnitRobust(p, source, target, viToEn);
            out.add(unit == null || unit.isBlank() ? p : unit);
        }

        return String.join(" ", out).replaceAll("\\s+", " ").trim();
    }

    private List<String> splitIntoChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // First split on sentence-like boundaries.
        String[] coarse = text.split("(?<=[.!?;\\n])\\s+");
        for (String raw : coarse) {
            String sentence = raw == null ? "" : raw.trim();
            if (sentence.isBlank()) continue;

            if (sentence.length() <= maxChars) {
                chunks.add(sentence);
                continue;
            }

            // Further split very long sentences by commas / spaces, keeping semantic coherence.
            String[] byComma = sentence.split("(?<=,)\\s+");
            StringBuilder current = new StringBuilder();

            for (String pieceRaw : byComma) {
                String piece = pieceRaw == null ? "" : pieceRaw.trim();
                if (piece.isBlank()) continue;

                if (piece.length() > maxChars) {
                    // Hard wrap on words when no punctuation helps.
                    String[] words = piece.split("\\s+");
                    for (String w : words) {
                        if (w == null || w.isBlank()) continue;
                        if (current.length() == 0) {
                            current.append(w);
                        } else if (current.length() + 1 + w.length() <= maxChars) {
                            current.append(' ').append(w);
                        } else {
                            chunks.add(current.toString().trim());
                            current.setLength(0);
                            current.append(w);
                        }
                    }
                    continue;
                }

                if (current.length() == 0) {
                    current.append(piece);
                } else if (current.length() + 1 + piece.length() <= maxChars) {
                    current.append(' ').append(piece);
                } else {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    current.append(piece);
                }
            }

            if (current.length() > 0) {
                chunks.add(current.toString().trim());
            }
        }

        return chunks;
    }

    private String translateUnitRobust(String text, String source, String target, boolean strictEnglish) {
        // Provider order for free/local stability:
        // 1) LibreTranslate public instance
        // 2) Argos local offline (if available)
        // 3) Google Cloud (if key exists)
        // 4) MyMemory
        // 5) Google public fallback

        String libre = retryProvider(() -> tryLibreTranslate(text, source, target), 1);
        if (isAcceptable(text, libre, strictEnglish)) return libre;

        String argos = retryProvider(() -> tryArgosLocal(text, source, target), 1);
        if (isAcceptable(text, argos, strictEnglish)) return argos;

        String cloud = retryProvider(() -> tryGoogleCloud(text, source, target), 2);
        if (isAcceptable(text, cloud, strictEnglish)) return cloud;

        String mm = retryProvider(() -> tryMyMemory(text, source, target), 2);
        if (isAcceptable(text, mm, strictEnglish)) return mm;

        String gpub = retryProvider(() -> tryGooglePublic(text, source, target), 2);
        if (isAcceptable(text, gpub, strictEnglish)) return gpub;

        // vi->en extra fallback: remove accents and retry to reduce mixed/encoded failures
        if (strictEnglish) {
            String ascii = removeVietnameseDiacritics(text);
            if (!ascii.equalsIgnoreCase(text) && !ascii.isBlank()) {
                String mmAscii = retryProvider(() -> tryMyMemory(ascii, source, target), 1);
                if (isAcceptable(ascii, mmAscii, true)) return mmAscii;

                String gAscii = retryProvider(() -> tryGooglePublic(ascii, source, target), 1);
                if (isAcceptable(ascii, gAscii, true)) return gAscii;
            }
        }

        return text;
    }

    private interface ProviderCall { String call(); }

    private String retryProvider(ProviderCall call, int tries) {
        for (int i = 0; i < tries; i++) {
            try {
                String v = cleanTranslation(call.call());
                if (v != null && !v.isBlank()) return v;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String tryLibreTranslate(String text, String source, String target) {
        if (!libreEnabled) return "";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("q", text);
            body.put("source", source);
            body.put("target", target);
            body.put("format", "text");

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.postForObject(libreUrl, req, Map.class);
            if (response == null) return "";

            Object translated = response.get("translatedText");
            if (translated == null) return "";
            return cleanTranslation(String.valueOf(translated));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String tryArgosLocal(String text, String source, String target) {
        if (!argosEnabled) return "";
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    argosPythonCommand,
                    argosScriptPath,
                    "--from", source,
                    "--to", target,
                    "--text", text
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            boolean finished = p.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "";
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    output.append(line);
                }
            }

            if (p.exitValue() != 0) return "";
            return cleanTranslation(output.toString());
        } catch (Exception ignored) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String tryGoogleCloud(String text, String source, String target) {
        if (googleApiKey == null || googleApiKey.isBlank()) return "";

        String url = "https://translation.googleapis.com/language/translate/v2?key=" +
                URLEncoder.encode(googleApiKey.trim(), StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("q", text);
        body.put("source", source);
        body.put("target", target);
        body.put("format", "text");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(url, req, Map.class);
        if (response == null) return "";

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) return "";
        List<Map<String, Object>> translations = (List<Map<String, Object>>) data.get("translations");
        if (translations == null || translations.isEmpty()) return "";

        String translated = String.valueOf(translations.get(0).getOrDefault("translatedText", ""));
        return decodeHtmlEntities(translated);
    }

    @SuppressWarnings("unchecked")
    private String tryMyMemory(String text, String source, String target) {
        String url = "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(text, StandardCharsets.UTF_8) +
                "&langpair=" + source + "|" + target;

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return "";

        Map<String, Object> responseData = (Map<String, Object>) response.get("responseData");
        String translatedText = responseData != null
                ? String.valueOf(responseData.getOrDefault("translatedText", ""))
                : "";

        translatedText = cleanTranslation(translatedText);
        if (translatedText != null && !translatedText.isBlank()) return translatedText;

        return extractBestMyMemoryMatch(response);
    }

    @SuppressWarnings("unchecked")
    private String tryGooglePublic(String text, String source, String target) {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
                source + "&tl=" + target + "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

        Object raw = restTemplate.getForObject(url, Object.class);
        if (!(raw instanceof List<?> outer) || outer.isEmpty()) return "";

        Object sentencesObj = outer.get(0);
        if (!(sentencesObj instanceof List<?> sentences)) return "";

        StringBuilder sb = new StringBuilder();
        for (Object s : sentences) {
            if (s instanceof List<?> row && !row.isEmpty() && row.get(0) != null) {
                sb.append(row.get(0));
            }
        }

        return cleanTranslation(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private String extractBestMyMemoryMatch(Map<String, Object> response) {
        Object matchesObj = response.get("matches");
        if (!(matchesObj instanceof List<?> matches) || matches.isEmpty()) return "";

        String best = "";
        double bestScore = -1.0;

        for (Object m : matches) {
            if (!(m instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> match = (Map<String, Object>) rawMap;
            Object translationObj = match.get("translation");
            if (translationObj == null) continue;

            String candidate = cleanTranslation(String.valueOf(translationObj));
            if (candidate == null || candidate.isBlank()) continue;

            double score = toDouble(match.get("match"));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private String cleanTranslation(String input) {
        if (input == null) return "";

        String out = input.trim();
        out = decodeHtmlEntities(out);
        out = out.replaceAll("%\\s+", "%");

        // multi decode for %C3... and nested encodings
        for (int i = 0; i < 4; i++) {
            try {
                String decoded = URLDecoder.decode(out, StandardCharsets.UTF_8);
                if (decoded.equals(out)) break;
                out = decoded;
                out = out.replaceAll("%\\s+", "%");
            } catch (Exception e) {
                break;
            }
        }

        return out.replaceAll("\\s+", " ").trim();
    }

    private String decodeHtmlEntities(String input) {
        if (input == null) return "";
        return input
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }

    private boolean isAcceptable(String src, String translated, boolean strictEnglish) {
        if (translated == null || translated.isBlank()) return false;

        String s = src.trim();
        String t = translated.trim();

        if (t.equalsIgnoreCase(s)) return false;
        if (looksEncodedJunk(t)) return false;
        if (strictEnglish && !isLikelyEnglishOnly(t)) return false;

        return true;
    }

    private boolean shouldCache(String src, String translated, boolean strictEnglish) {
        if (!isAcceptable(src, translated, strictEnglish)) return false;

        // Avoid caching suspiciously short outputs for long inputs.
        if (src.length() > 80 && translated.length() < 12) return false;

        return true;
    }

    private boolean looksEncodedJunk(String text) {
        // Examples: %C3%A0, %E1%BB%... remaining in output
        return text.matches(".*%[0-9A-Fa-f]{2}.*");
    }

    private String removeVietnameseDiacritics(String input) {
        String s = input.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private boolean isLikelyEnglishOnly(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();

        // reject accented Vietnamese chars
        if (lower.matches(".*[đăâêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ].*")) {
            return false;
        }

        // reject common unaccented Vietnamese token patterns
        String normalized = lower.replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) return false;

        String[] viTokens = {"toi", "ban", "va", "khong", "co", "ngay", "mai", "choi", "thoi", "nao", "viec", "moi", "di", "la", "mot", "cua", "duoc", "anh", "viet"};
        int hit = 0;
        for (String t : viTokens) {
            if (normalized.matches(".*\\b" + t + "\\b.*")) hit++;
            if (hit >= 2) return false;
        }

        return true;
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
}

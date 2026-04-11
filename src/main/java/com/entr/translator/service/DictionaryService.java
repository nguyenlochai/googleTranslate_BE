package com.entr.translator.service;

import com.entr.translator.dto.DictionaryResponse;
import com.entr.translator.model.LookupHistory;
import com.entr.translator.model.User;
import com.entr.translator.repository.LookupHistoryRepository;
import com.entr.translator.repository.UserRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DictionaryService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final LookupHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final com.entr.translator.repository.VocabularyRepository vocabRepository;

    public DictionaryService(LookupHistoryRepository historyRepository, UserRepository userRepository, com.entr.translator.repository.VocabularyRepository vocabRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.vocabRepository = vocabRepository;
    }

    public DictionaryResponse lookup(String word, String userEmail) {
        int currentMastery = 0;
        int currentFluency = 0;

        // Try to get existing mastery for user
        if (userEmail != null && !userEmail.isBlank()) {
            Optional<User> userOpt = userRepository.findByEmail(userEmail);
            if (userOpt.isPresent()) {
                List<LookupHistory> existing = historyRepository.findTop20ByUserIdOrderByCreatedAtDesc(userOpt.get().getId());
                existing.stream()
                        .filter(h -> h.getQueryText().equalsIgnoreCase(word))
                        .findFirst()
                        .ifPresent(h -> {
                            // Fetch scores safely
                        });
                
                // Fetch actual scores from most recent history specifically
                Optional<LookupHistory> lastEntry = existing.stream()
                        .filter(h -> h.getQueryText().equalsIgnoreCase(word))
                        .findFirst();
                if (lastEntry.isPresent()) {
                    currentMastery = lastEntry.get().getMasteryScore();
                    currentFluency = lastEntry.get().getFluencyScore();
                }
            }
        }

        // Check our cache first
        Optional<com.entr.translator.model.Vocabulary> cached = vocabRepository.findByWordIgnoreCase(word);
        if (cached.isPresent()) {
            com.entr.translator.model.Vocabulary v = cached.get();
            return new DictionaryResponse(word, v.getPhonetic(), v.getMeanings(), v.getExamples(), v.getSynonyms(), v.getAntonyms(), List.of(), "", currentMastery, currentFluency);
        }

        String url = "https://api.dictionaryapi.dev/api/v2/entries/en/" + word;
        try {
            boolean isPhrase = word.trim().contains(" ");
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                return new DictionaryResponse(word, "", List.of("A common phrase used in English."), List.of(), List.of(), List.of(), List.of(), url, currentMastery, currentFluency);
            }

            Map<String, Object> first = body.get(0);
            String phonetic = (String) first.getOrDefault("phonetic", "");
            if (phonetic == null) phonetic = "";

            // Try to extract phonetic from phonetics array if top-level phonetic is empty
            if (phonetic.isBlank()) {
                Object phoneticsObj = first.get("phonetics");
                if (phoneticsObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Object textObj = m.get("text");
                            if (textObj != null) {
                                String t = textObj.toString().trim();
                                if (!t.isBlank()) {
                                    phonetic = t;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            List<String> meanings = new ArrayList<>();
            List<String> examples = new ArrayList<>();
            List<String> synonyms = new ArrayList<>();
            List<String> antonyms = new ArrayList<>();

            List<Map<String, Object>> meaningsRaw = (List<Map<String, Object>>) first.get("meanings");
            if (meaningsRaw != null) {
                for (Map<String, Object> meaning : meaningsRaw) {
                    
                    // Defensive extract synonyms/antonyms from meaning 
                    Object synsObj = meaning.get("synonyms");
                    if (synsObj instanceof List<?> list) {
                        for (Object s : list) if (s != null) synonyms.add(s.toString());
                    }
                    
                    Object antsObj = meaning.get("antonyms");
                    if (antsObj instanceof List<?> list) {
                        for (Object a : list) if (a != null) antonyms.add(a.toString());
                    }

                    List<Map<String, Object>> defs = (List<Map<String, Object>>) meaning.get("definitions");
                    if (defs == null) continue;
                    for (Map<String, Object> def : defs) {
                        Object definition = def.get("definition");
                        Object example = def.get("example");
                        
                        // Defensive extract from definitions
                        Object dSyns = def.get("synonyms");
                        if (dSyns instanceof List<?> list) {
                            for (Object s : list) if (s != null) synonyms.add(s.toString());
                        }
                        
                        Object dAnts = def.get("antonyms");
                        if (dAnts instanceof List<?> list) {
                            for (Object a : list) if (a != null) antonyms.add(a.toString());
                        }

                        if (definition != null) meanings.add(definition.toString());
                        if (example != null) examples.add(example.toString());
                    }
                }
            }

            if (meanings.isEmpty()) {
                meanings.add("A common English " + (isPhrase ? "phrase" : "word") + " used in daily communication.");
            }
            if (examples.isEmpty()) {
                if (isPhrase) {
                    String clean = word.toLowerCase().trim();
                    if (clean.startsWith("i want to")) {
                        examples.add("I want to " + clean.substring(9).trim() + " something healthy for dinner tonight.");
                    } else if (clean.startsWith("how to")) {
                        examples.add("Can you show me how to " + clean.substring(6).trim() + " this correctly?");
                    } else {
                        examples.add("Native speakers often say '" + word + "' during casual conversations.");
                    }
                } else {
                    examples.add("In a professional setting, we might use the word '" + word + "' to sound more precise.");
                    examples.add("Could you explain what '" + word + "' means in this specific context?");
                }
            }

            // Cache this new vocabulary data
            try {
                com.entr.translator.model.Vocabulary newVocab = new com.entr.translator.model.Vocabulary();
                newVocab.setWord(word);
                newVocab.setPhonetic(phonetic);
                newVocab.setMeanings(meanings);
                newVocab.setExamples(examples);
                newVocab.setSynonyms(synonyms.stream().distinct().toList());
                newVocab.setAntonyms(antonyms.stream().distinct().toList());
                vocabRepository.save(newVocab);
            } catch (Exception e) {
                // Silently fail if word already exists or other DB error
            }

            // Save History for user
            if (userEmail != null && !userEmail.isBlank()) {
                Optional<User> userOpt = userRepository.findByEmail(userEmail);
                if (userOpt.isPresent()) {
                    LookupHistory history = new LookupHistory();
                    history.setUser(userOpt.get());
                    history.setQueryText(word);
                    history.setResultSummary(meanings.isEmpty() ? "" : meanings.get(0));
                    history.setSourceLang("en");
                    history.setTargetLang("vi");
                    history.setMasteryScore(currentMastery);
                    history.setFluencyScore(currentFluency);
                    historyRepository.save(history);
                }
            }

            return new DictionaryResponse(
                word, 
                phonetic, 
                meanings, 
                examples, 
                synonyms.stream().distinct().toList(), 
                antonyms.stream().distinct().toList(), 
                List.of(), // relatedWords
                url, 
                currentMastery, 
                currentFluency
            );
        } catch (Exception e) {
            return new DictionaryResponse(word, "", List.of(), List.of(), List.of(), List.of(), List.of(), "", currentMastery, currentFluency);
        }
    }
}

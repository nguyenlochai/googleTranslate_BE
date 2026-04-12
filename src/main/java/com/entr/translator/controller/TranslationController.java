package com.entr.translator.controller;

import com.entr.translator.dto.DictionaryResponse;
import com.entr.translator.dto.TranslateRequest;
import com.entr.translator.dto.TranslateResponse;
import com.entr.translator.model.LookupHistory;
import com.entr.translator.model.User;
import com.entr.translator.repository.LookupHistoryRepository;
import com.entr.translator.repository.UserRepository;
import com.entr.translator.security.AuthHelper;
import com.entr.translator.service.DictionaryService;
import com.entr.translator.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/public")
public class TranslationController {
    private final TranslationService translationService;
    private final DictionaryService dictionaryService;
    private final UserRepository userRepository;
    private final LookupHistoryRepository historyRepository;
    private final AuthHelper authHelper;

    public TranslationController(
            TranslationService translationService,
            DictionaryService dictionaryService,
            UserRepository userRepository,
            LookupHistoryRepository historyRepository,
            AuthHelper authHelper
    ) {
        this.translationService = translationService;
        this.dictionaryService = dictionaryService;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.authHelper = authHelper;
    }

    @PostMapping("/translate")
    public TranslateResponse translate(
            @Valid @RequestBody TranslateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String email
    ) {
        TranslateResponse res = translationService.translate(request.text(), request.source(), request.target());

        // Save history for logged-in users (token preferred). Keeps Dashboard history in sync.
        String resolvedEmail = authHelper.resolveEmail(email, authorization);
        if (resolvedEmail != null) {
            Optional<User> userOpt = userRepository.findByEmail(resolvedEmail);
            if (userOpt.isPresent()) {
                String q = request.text() == null ? "" : request.text().trim();
                if (!q.isBlank()) {
                    LookupHistory h = new LookupHistory();
                    h.setUser(userOpt.get());
                    h.setQueryText(q);
                    h.setResultSummary(res.translatedText());
                    h.setSourceLang(request.source());
                    h.setTargetLang(request.target());
                    historyRepository.save(h);
                }
            }
        }

        return res;
    }

    @GetMapping("/dictionary")
    public DictionaryResponse dictionary(
            @RequestParam String word,
            @RequestParam(required = false) String email,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String resolvedEmail = authHelper.resolveEmail(email, authorization);
        return dictionaryService.lookup(word, resolvedEmail);
    }
}

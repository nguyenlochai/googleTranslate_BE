package com.entr.translator.controller;

import com.entr.translator.dto.DictionaryResponse;
import com.entr.translator.dto.TranslateRequest;
import com.entr.translator.dto.TranslateResponse;
import com.entr.translator.service.DictionaryService;
import com.entr.translator.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class TranslationController {
    private final TranslationService translationService;
    private final DictionaryService dictionaryService;

    public TranslationController(TranslationService translationService, DictionaryService dictionaryService) {
        this.translationService = translationService;
        this.dictionaryService = dictionaryService;
    }

    @PostMapping("/translate")
    public TranslateResponse translate(@Valid @RequestBody TranslateRequest request) {
        return translationService.translate(request.text(), request.source(), request.target());
    }

    @GetMapping("/dictionary")
    public DictionaryResponse dictionary(@RequestParam String word, @RequestParam(required = false) String email) {
        return dictionaryService.lookup(word, email);
    }
}

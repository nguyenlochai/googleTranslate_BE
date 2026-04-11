package com.entr.translator.controller;

import com.entr.translator.dto.GrammarRequest;
import com.entr.translator.dto.GrammarResponse;
import com.entr.translator.service.GrammarService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class GrammarController {
    private final GrammarService grammarService;

    public GrammarController(GrammarService grammarService) {
        this.grammarService = grammarService;
    }

    @PostMapping("/grammar/analyze")
    public GrammarResponse analyze(@Valid @RequestBody GrammarRequest request) {
        return grammarService.analyze(request.text());
    }
}

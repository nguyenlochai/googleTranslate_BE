package com.entr.translator.controller;

import com.entr.translator.dto.TutorChatRequest;
import com.entr.translator.dto.TutorChatResponse;
import com.entr.translator.service.TutorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/tutor")
public class TutorController {
    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping("/chat")
    public TutorChatResponse chat(@Valid @RequestBody TutorChatRequest request) {
        return tutorService.chat(request.message(), request.email());
    }
}

package com.entr.translator.controller;

import com.entr.translator.model.LookupHistory;
import com.entr.translator.model.User;
import com.entr.translator.repository.LookupHistoryRepository;
import com.entr.translator.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/history")
public class HistoryController {
    private final LookupHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public HistoryController(LookupHistoryRepository historyRepository, UserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<LookupHistory> getHistory(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return historyRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @PostMapping("/mastery")
    public void updateMastery(
            @RequestParam String email, 
            @RequestParam String word, 
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) Integer fluency) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Find most recent lookup for this word
        List<LookupHistory> history = historyRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.getId());
        history.stream()
                .filter(h -> h.getQueryText().equalsIgnoreCase(word))
                .findFirst()
                .ifPresent(h -> {
                    if (score != null) h.setMasteryScore(score);
                    if (fluency != null) h.setFluencyScore(fluency);
                    historyRepository.save(h);
                });
    }
}

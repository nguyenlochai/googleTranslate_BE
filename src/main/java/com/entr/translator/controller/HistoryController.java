package com.entr.translator.controller;

import com.entr.translator.model.LookupHistory;
import com.entr.translator.model.User;
import com.entr.translator.repository.LookupHistoryRepository;
import com.entr.translator.repository.UserRepository;
import com.entr.translator.security.AuthHelper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/history")
public class HistoryController {
    private final LookupHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AuthHelper authHelper;

    public HistoryController(LookupHistoryRepository historyRepository, UserRepository userRepository, AuthHelper authHelper) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.authHelper = authHelper;
    }

    @GetMapping
    public List<LookupHistory> getHistory(
            @RequestParam(required = false) String email,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String resolvedEmail = authHelper.resolveEmail(email, authorization);
        if (resolvedEmail == null) throw new RuntimeException("Unauthorized");

        User user = userRepository.findByEmail(resolvedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return historyRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @PostMapping("/mastery")
    public void updateMastery(
            @RequestParam(required = false) String email,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String word,
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) Integer fluency
    ) {
        String resolvedEmail = authHelper.resolveEmail(email, authorization);
        if (resolvedEmail == null) throw new RuntimeException("Unauthorized");

        User user = userRepository.findByEmail(resolvedEmail)
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

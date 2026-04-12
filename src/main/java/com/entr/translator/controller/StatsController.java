package com.entr.translator.controller;

import com.entr.translator.model.QuizHistory;
import com.entr.translator.model.LookupHistory;
import com.entr.translator.repository.QuizHistoryRepository;
import com.entr.translator.repository.UserRepository;
import com.entr.translator.repository.VocabularyRepository;
import com.entr.translator.repository.LookupHistoryRepository;
import com.entr.translator.security.AuthHelper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/stats")
public class StatsController {
    private final VocabularyRepository vocabularyRepository;
    private final QuizHistoryRepository quizHistoryRepository;
    private final LookupHistoryRepository lookupHistoryRepository;
    private final UserRepository userRepository;
    private final AuthHelper authHelper;

    public StatsController(VocabularyRepository vocabularyRepository,
                           QuizHistoryRepository quizHistoryRepository,
                           LookupHistoryRepository lookupHistoryRepository,
                           UserRepository userRepository,
                           AuthHelper authHelper) {
        this.vocabularyRepository = vocabularyRepository;
        this.quizHistoryRepository = quizHistoryRepository;
        this.lookupHistoryRepository = lookupHistoryRepository;
        this.userRepository = userRepository;
        this.authHelper = authHelper;
    }

    @GetMapping
    public Map<String, Object> getStats(
            @RequestParam(required = false) String email,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String resolvedEmail = authHelper.resolveEmail(email, authorization);
        if (resolvedEmail == null) return Map.of("error", "Unauthorized");

        return userRepository.findByEmail(resolvedEmail).map(user -> {
            Map<String, Object> stats = new HashMap<>();

            // 1. Vocabulary stats
            long totalVocab = vocabularyRepository.count();
            stats.put("totalVocab", totalVocab);

            // 2. Quiz stats
            List<QuizHistory> quizHistory = quizHistoryRepository.findByUserIdOrderByPlayedAtDesc(user.getId());
            int totalQuizScore = quizHistory.stream().mapToInt(QuizHistory::getScore).sum();
            int totalGames = quizHistory.size();
            stats.put("totalQuizScore", totalQuizScore);
            stats.put("totalGames", totalGames);
            stats.put("recentQuizzes", quizHistory.stream().limit(5).collect(Collectors.toList()));

            // 3. Mastery stats
            double avgMastery = lookupHistoryRepository.findByUserId(user.getId()).stream()
                    .mapToInt(LookupHistory::getMasteryScore)
                    .average().orElse(0.0);
            stats.put("avgMastery", Math.round(avgMastery * 10.0) / 10.0);

            return stats;
        }).orElse(Map.of("error", "User not found"));
    }
}

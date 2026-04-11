package com.entr.translator.repository;

import com.entr.translator.model.QuizHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {
    List<QuizHistory> findByUserIdOrderByPlayedAtDesc(Long userId);
}

package com.entr.translator.repository;

import com.entr.translator.model.LookupHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LookupHistoryRepository extends JpaRepository<LookupHistory, Long> {
    List<LookupHistory> findByUserId(Long userId);
    List<LookupHistory> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}

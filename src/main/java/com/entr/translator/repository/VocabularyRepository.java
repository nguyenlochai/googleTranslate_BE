package com.entr.translator.repository;

import com.entr.translator.model.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {
    Optional<Vocabulary> findByWordIgnoreCase(String word);
}

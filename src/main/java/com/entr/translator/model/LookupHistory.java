package com.entr.translator.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lookup_history")
public class LookupHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String queryText;
    private String sourceLang;
    private String targetLang;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private int masteryScore = 0;
    private int fluencyScore = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String sourceLang) { this.sourceLang = sourceLang; }
    public String getTargetLang() { return targetLang; }
    public void setTargetLang(String targetLang) { this.targetLang = targetLang; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public int getMasteryScore() { return masteryScore; }
    public void setMasteryScore(int masteryScore) { this.masteryScore = masteryScore; }
    public int getFluencyScore() { return fluencyScore; }
    public void setFluencyScore(int fluencyScore) { this.fluencyScore = fluencyScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}

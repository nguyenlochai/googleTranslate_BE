package com.entr.translator.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "vocabularies")
public class Vocabulary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String word;

    private String phonetic;
    
    @Column(columnDefinition = "TEXT")
    private String partOfSpeech;

    @ElementCollection
    @CollectionTable(name = "vocabulary_meanings", joinColumns = @JoinColumn(name = "vocabulary_id"))
    @Column(name = "meaning", columnDefinition = "TEXT")
    private List<String> meanings;

    @ElementCollection
    @CollectionTable(name = "vocabulary_examples", joinColumns = @JoinColumn(name = "vocabulary_id"))
    @Column(name = "example", columnDefinition = "TEXT")
    private List<String> examples;

    @ElementCollection
    @CollectionTable(name = "vocabulary_synonyms", joinColumns = @JoinColumn(name = "vocabulary_id"))
    @Column(name = "synonym")
    private List<String> synonyms;

    @ElementCollection
    @CollectionTable(name = "vocabulary_antonyms", joinColumns = @JoinColumn(name = "vocabulary_id"))
    @Column(name = "antonym")
    private List<String> antonyms;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public List<String> getMeanings() { return meanings; }
    public void setMeanings(List<String> meanings) { this.meanings = meanings; }
    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }
    public List<String> getSynonyms() { return synonyms; }
    public void setSynonyms(List<String> synonyms) { this.synonyms = synonyms; }
    public List<String> getAntonyms() { return antonyms; }
    public void setAntonyms(List<String> antonyms) { this.antonyms = antonyms; }
}

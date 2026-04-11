package com.entr.translator.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/sentences")
public class SentenceController {

    @GetMapping("/random")
    public List<Map<String, Object>> getRandomSentences(@RequestParam(defaultValue = "10") int limit) {
        List<String> pool = new ArrayList<>(Arrays.asList(
            "I am a student",
            "The weather is beautiful today",
            "I love learning English",
            "She is working at the office",
            "Success comes with hard work",
            "Do you like to travel",
            "They are playing in the park",
            "Please open the door",
            "The book is on the table",
            "Have a nice day",
            "Where are you from",
            "I like to eat apples",
            "He is a very good doctor",
            "We are going to the cinema",
            "Can you help me please",
            "It is a long journey",
            "The cat is sleeping on the sofa",
            "I want to drink some water",
            "She speaks English very well",
            "My father is a teacher",
            "What time is it now",
            "I have a big family",
            "The sky is blue and clear",
            "He lives in a small house",
            "We need to go home now",
            "I am happy to see you",
            "The flowers are very pretty",
            "Can I borrow your pen",
            "They are my best friends",
            "I am learning how to swim",
            "The coffee is too hot",
            "He is wearing a red shirt",
            "We saw a movie last night",
            "I will call you tomorrow",
            "Please sit down and listen",
            "The music is very loud",
            "I forgot my umbrella today",
            "She is a very talented singer",
            "We are planning a trip",
            "He works at a bank",
            "The car is very fast",
            "I like to listen to music",
            "They are visiting their grandparents",
            "It is raining outside now",
            "I want to buy a new car",
            "She is cooking dinner for us",
            "We are studying for the exam",
            "He is playing the guitar",
            "The students are in the classroom",
            "I have two brothers and one sister",
            "The food is very delicious",
            "I enjoy walking in the park",
            "The sun rises in the east",
            "She has a beautiful voice",
            "He is a very kind person",
            "We are having a great time",
            "I need to buy some milk",
            "The baby is sleeping peacefully",
            "They are moving to a new house",
            "I am waiting for the bus",
            "The mountains are covered with snow",
            "She is reading a book",
            "He is driving a blue car",
            "We are going to the beach",
            "I like to play football",
            "The library is a quiet place",
            "He is a very smart boy",
            "We are eating lunch together",
            "I have a lot of work to do",
            "The children are playing outside",
            "She is a very creative artist",
            "We are looking for a hotel",
            "He is taking a shower",
            "The movie was very interesting",
            "I am looking for my keys",
            "The garden is full of flowers",
            "She is wearing a white dress",
            "He is a very fast runner",
            "We are waiting for the train",
            "I like to ride my bike",
            "The shop is closed today",
            "He is a very famous actor",
            "We are having dinner at seven",
            "I need to go to the bank",
            "The window is open",
            "She is a very helpful person",
            "He is playing with his dog",
            "The park is very crowded today",
            "I want to travel around the world",
            "She is a very good cook",
            "We are staying at a nice hotel",
            "He is reading the newspaper",
            "The birds are singing in the trees",
            "I like to go shopping",
            "She is a very elegant woman",
            "We are going to the zoo",
            "He is a very strong man",
            "The beach is very clean",
            "I am writing a letter",
            "The cake was very sweet",
            "She is a very friendly girl",
            "He is a very busy man",
            "We are going on a hike",
            "I like to watch TV",
            "The sea is very calm"
        ));

        Collections.shuffle(pool);
        List<String> selected = pool.stream().limit(limit).collect(Collectors.toList());
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (String s : selected) {
            Map<String, Object> map = new HashMap<>();
            map.put("full", s);
            map.put("words", Arrays.asList(s.split(" ")));
            result.add(map);
        }
        return result;
    }
}

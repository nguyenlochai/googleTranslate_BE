package com.entr.translator.controller;

import com.entr.translator.model.QuizHistory;
import com.entr.translator.model.User;
import com.entr.translator.model.Vocabulary;
import com.entr.translator.repository.QuizHistoryRepository;
import com.entr.translator.repository.UserRepository;
import com.entr.translator.repository.VocabularyRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/quiz")
public class QuizController {
    private final VocabularyRepository vocabularyRepository;
    private final QuizHistoryRepository quizHistoryRepository;
    private final UserRepository userRepository;

    public QuizController(VocabularyRepository vocabularyRepository, 
                          QuizHistoryRepository quizHistoryRepository, 
                          UserRepository userRepository) {
        this.vocabularyRepository = vocabularyRepository;
        this.quizHistoryRepository = quizHistoryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/questions")
    public List<Map<String, Object>> getQuestions(@RequestParam(defaultValue = "10") int limit, @RequestParam(required = false) String email) {
        List<Vocabulary> allVocabs = vocabularyRepository.findAll();
        
        // Try to filter out recently played words if user is provided
        List<String> recentWords = new ArrayList<>();
        if (email != null && !email.isBlank()) {
           // We could track recent words in a more complex way, 
           // but for now let's just shuffle the whole pool better
        }

        if (allVocabs.size() < 4) {
            return generateFallbackQuestions();
        }

        Collections.shuffle(allVocabs);
        
        // Strategy: Sort by unplayed or low frequency in history if we had that data, 
        // for now, just extreme shuffle
        List<Vocabulary> selected = allVocabs.stream().limit(limit).collect(Collectors.toList());
        List<Map<String, Object>> questions = new ArrayList<>();

        for (Vocabulary v : selected) {
            String correctMeaning = v.getMeanings().isEmpty() ? "No definition" : v.getMeanings().get(0);
            
            // Get 3 wrong meanings
            List<String> others = allVocabs.stream()
                    .filter(o -> !o.getWord().equals(v.getWord()))
                    .map(o -> o.getMeanings().isEmpty() ? "Other meaning" : o.getMeanings().get(0))
                    .distinct()
                    .collect(Collectors.toList());
            Collections.shuffle(others);
            
            List<String> options = new ArrayList<>();
            options.add(correctMeaning);
            options.addAll(others.subList(0, Math.min(3, others.size())));
            Collections.shuffle(options);

            Map<String, Object> q = new HashMap<>();
            q.put("word", v.getWord());
            q.put("options", options);
            q.put("correct", options.indexOf(correctMeaning));
            questions.add(q);
        }
        return questions;
    }

    @PostMapping("/score")
    public void saveScore(
            @RequestParam String email, 
            @RequestParam int score, 
            @RequestParam int total,
            @RequestParam int correctCount,
            @RequestParam String wordsTested) {
        userRepository.findByEmail(email).ifPresent(user -> {
            QuizHistory history = new QuizHistory();
            history.setUser(user);
            history.setScore(score);
            history.setTotalQuestions(total);
            history.setCorrectCount(correctCount);
            history.setWordsTested(wordsTested);
            quizHistoryRepository.save(history);
        });
    }

    @GetMapping("/history")
    public List<QuizHistory> getHistory(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .map(u -> quizHistoryRepository.findByUserIdOrderByPlayedAtDesc(u.getId()))
                .orElse(List.of());
    }

    private List<Map<String, Object>> generateFallbackQuestions() {
        List<Map<String, Object>> pool = new ArrayList<>();
        // Group 1: Common Nouns & Objects
        pool.add(Map.of("word", "Apple", "options", List.of("Quả táo", "Con mèo", "Cái nhà", "Xe hơi"), "correct", 0));
        pool.add(Map.of("word", "Success", "options", List.of("Thất bại", "Cố gắng", "Thành công", "Kết thúc"), "correct", 2));
        pool.add(Map.of("word", "Brave", "options", List.of("Sợ hãi", "Dũng cảm", "Yếu đuối", "Buồn bã"), "correct", 1));
        pool.add(Map.of("word", "Challenge", "options", List.of("Dễ dàng", "Phần thưởng", "Thử thách", "Bắt đầu"), "correct", 2));
        pool.add(Map.of("word", "Delicious", "options", List.of("Đắng", "Cay", "Chua", "Ngon"), "correct", 3));
        pool.add(Map.of("word", "Adventure", "options", List.of("Nghỉ ngơi", "Cuộc họp", "Phiêu lưu", "Đi học"), "correct", 2));
        pool.add(Map.of("word", "Knowledge", "options", List.of("Kỹ năng", "Kinh nghiệm", "Kiến thức", "Hành động"), "correct", 2));
        pool.add(Map.of("word", "Powerful", "options", List.of("Yếu đuối", "Quyền năng", "Chậm chạp", "Nhanh nhẹn"), "correct", 1));
        pool.add(Map.of("word", "Honest", "options", List.of("Trung thực", "Dối trá", "Lười biếng", "Chăm chỉ"), "correct", 0));
        pool.add(Map.of("word", "Creative", "options", List.of("Lặp lại", "Nhàm chán", "Sáng tạo", "Cẩn thận"), "correct", 2));
        
        // Group 2: Adjectives & Qualities
        pool.add(Map.of("word", "Flexible", "options", List.of("Cứng nhắc", "Linh hoạt", "Cố định", "Hỏng"), "correct", 1));
        pool.add(Map.of("word", "Freedom", "options", List.of("Tự do", "Giam cầm", "Ràng buộc", "Nghèo đói"), "correct", 0));
        pool.add(Map.of("word", "Imagine", "options", List.of("Thực tế", "Quên", "Tưởng tượng", "Nhớ"), "correct", 2));
        pool.add(Map.of("word", "Harmony", "options", List.of("Ồn ào", "Xung đột", "Hòa hợp", "Tách biệt"), "correct", 2));
        pool.add(Map.of("word", "Inspiration", "options", List.of("Chán nản", "Cảm hứng", "Thất vọng", "Mệt mỏi"), "correct", 1));
        pool.add(Map.of("word", "Justice", "options", List.of("Bất công", "Công lý", "Luật pháp", "Tiền bạc"), "correct", 1));
        pool.add(Map.of("word", "Loyalty", "options", List.of("Phản bội", "Trung thành", "Giả dối", "Sợ hãi"), "correct", 1));
        pool.add(Map.of("word", "Motivation", "options", List.of("Động lực", "Lười biếng", "Dừng lại", "Kết thúc"), "correct", 0));
        pool.add(Map.of("word", "Nervous", "options", List.of("Tự tin", "Lo lắng", "Bình tĩnh", "Vui vẻ"), "correct", 1));
        pool.add(Map.of("word", "Patient", "options", List.of("Vội vã", "Nóng nảy", "Kiên nhẫn", "Chậm"), "correct", 2));
        
        // Group 3: Verbs & Actions
        pool.add(Map.of("word", "Achieve", "options", List.of("Thất bại", "Đạt được", "Mất", "Từ bỏ"), "correct", 1));
        pool.add(Map.of("word", "Believe", "options", List.of("Nghi ngờ", "Tin tưởng", "Quên", "Biết"), "correct", 1));
        pool.add(Map.of("word", "Collaborate", "options", List.of("Cạnh tranh", "Làm việc nhóm", "Cái tên", "Kết thúc"), "correct", 1));
        pool.add(Map.of("word", "Discover", "options", List.of("Che giấu", "Khám phá", "Mất", "Tìm kiếm"), "correct", 1));
        pool.add(Map.of("word", "Explore", "options", List.of("Dừng lại", "Thăm dò", "Xây dựng", "Mở"), "correct", 1));
        pool.add(Map.of("word", "Improve", "options", List.of("Làm tệ đi", "Cải thiện", "Giữ nguyên", "Thay đổi"), "correct", 1));
        pool.add(Map.of("word", "Maintain", "options", List.of("Phá hủy", "Duy trì", "Bỏ rơi", "Xây"), "correct", 1));
        pool.add(Map.of("word", "Participate", "options", List.of("Rút lui", "Tham gia", "Quan sát", "Ngăn chặn"), "correct", 1));
        pool.add(Map.of("word", "Recognize", "options", List.of("Quên", "Công nhận", "Bỏ qua", "Nhìn"), "correct", 1));
        pool.add(Map.of("word", "Succeed", "options", List.of("Thất bại", "Thành công", "Thử", "Bắt đầu"), "correct", 1));
        
        // Add more words to reach ~100
        String[][] basicWords = {
            {"Happy", "Hạnh phúc", "Buồn", "Giận", "Lo"},
            {"Sad", "Buồn", "Vui", "Đói", "Mệt"},
            {"Fast", "Nhanh", "Chậm", "To", "Nhỏ"},
            {"Slow", "Chậm", "Nhanh", "Cao", "Thấp"},
            {"Big", "To lớn", "Nhỏ", "Mỏng", "Dày"},
            {"Small", "Nhỏ", "To", "Rộng", "Hẹp"},
            {"Bright", "Sáng", "Tối", "Mờ", "Đục"},
            {"Dark", "Tối", "Sáng", "Màu", "Trắng"},
            {"Strong", "Mạnh", "Yếu", "Mềm", "Cứng"},
            {"Weak", "Yếu", "Mạnh", "Dẻo", "Dai"},
            {"Quiet", "Yên tĩnh", "Ồn ào", "Nhanh", "Chậm"},
            {"Loud", "Ồn ào", "Yên tĩnh", "Nhỏ", "To"},
            {"Old", "Cũ/Già", "Mới", "Trẻ", "Nhanh"},
            {"New", "Mới", "Cũ", "Đắt", "Rẻ"},
            {"Rich", "Giàu", "Nghèo", "Đói", "Khát"},
            {"Poor", "Nghèo", "Giàu", "Vui", "Buồn"},
            {"Hot", "Nóng", "Lạnh", "Ấm", "Mát"},
            {"Cold", "Lạnh", "Nóng", "Khô", "Ướt"},
            {"Easy", "Dễ", "Khó", "Nhuần", "Nhuyễn"},
            {"Hard", "Khó/Cứng", "Dễ", "Mềm", "Lỏng"},
            {"Friend", "Bạn bè", "Kẻ thù", "Người lạ", "Người thân"},
            {"Family", "Gia đình", "Bạn bè", "Công ty", "Trường học"},
            {"School", "Trường học", "Bệnh viện", "Công viên", "Chợ"},
            {"Hospital", "Bệnh viện", "Trường học", "Sân bay", "Nhà ga"},
            {"Market", "Chợ", "Siêu thị", "Cửa hàng", "Nhà hàng"},
            {"Restaurant", "Nhà hàng", "Khách sạn", "Văn phòng", "Phòng tập"},
            {"Hotel", "Khách sạn", "Nhà ở", "Căn hộ", "Phòng"},
            {"Office", "Văn phòng", "Nhà máy", "Cửa hiệu", "Kho"},
            {"Dream", "Giấc mơ", "Thực tại", "Ác mộng", "Suy nghĩ"},
            {"Hope", "Hy vọng", "Thất vọng", "Lo lâu", "Sợ hãi"},
            {"Love", "Yêu", "Ghét", "Giận", "Thương"},
            {"Hate", "Ghét", "Yêu", "Thích", "Mến"},
            {"Peace", "Hòa bình", "Chiến tranh", "Ồn ào", "Hỗn loạn"},
            {"War", "Chiến tranh", "Hòa bình", "Ổn định", "Hợp tác"},
            {"Water", "Nước", "Lửa", "Đất", "Khí"},
            {"Fire", "Lửa", "Nước", "Gió", "Đá"},
            {"Earth", "Trái đất", "Mặt trăng", "Mặt trời", "Sao hỏa"},
            {"Sky", "Bầu trời", "Mặt đất", "Biển", "Rừng"},
            {"Mountain", "Núi", "Đồi", "Thung lũng", "Cánh đồng"},
            {"Ocean", "Đại dương", "Sông", "Hồ", "Suối"},
            {"River", "Sông", "Biển", "Ao", "Hồ"},
            {"Lake", "Hồ", "Sông", "Suối", "Biển"},
            {"Tree", "Cây", "Hoa", "Cỏ", "Lá"},
            {"Flower", "Hoa", "Cây", "Cành", "Rễ"},
            {"Leaf", "Lá", "Cành", "Rễ", "Thân"},
            {"Animal", "Động vật", "Thực vật", "Con người", "Máy móc"},
            {"Human", "Con người", "Robot", "Quái vật", "Thú"},
            {"Modern", "Hiện đại", "Cổ điển", "Cũ", "Lạc hậu"},
            {"Classic", "Cổ điển", "Hiện đại", "Mới", "Lạ"},
            {"Busy", "Bận rộn", "Rảnh rỗi", "Lười", "Chăm"},
            {"Lazy", "Lười biếng", "Chăm chỉ", "Bận", "Nhanh"},
            {"Clever", "Thông minh", "Ngốc xếch", "Xấu", "Đẹp"},
            {"Stupid", "Ngốc nghếch", "Thông minh", "Giỏi", "Nhanh"},
            {"Simple", "Đơn giản", "Phức tạp", "Khó", "Dễ"},
            {"Complex", "Phức tạp", "Đơn giản", "Dễ", "Gọn"},
            {"Morning", "Buổi sáng", "Buổi chiều", "Buổi tối", "Đêm"},
            {"Afternoon", "Buổi chiều", "Buổi sáng", "Buổi trưa", "Đêm"},
            {"Evening", "Buổi tối", "Buổi sáng", "Trưa", "Sáng"},
            {"Night", "Đêm", "Ngày", "Sáng", "Trưa"},
            {"Sun", "Mặt trời", "Mặt trăng", "Ngôi sao", "Mây"},
            {"Moon", "Mặt trăng", "Mặt trời", "Trái đất", "Sao"},
            {"Star", "Ngôi sao", "Hành tinh", "Thiên thạch", "Mây"},
            {"Cloud", "Mây", "Mưa", "Gió", "Nắng"},
            {"Rain", "Mưa", "Nắng", "Tuyết", "Bão"},
            {"Snow", "Tuyết", "Mưa", "Đá", "Sương"},
            {"Wind", "Gió", "Bão", "Lốc", "Mưa"},
            {"Storm", "Bão", "Mưa nhẹ", "Nắng", "Gió"},
            {"Season", "Mùa", "Tháng", "Năm", "Tuần"},
            {"Spring", "Mùa xuân", "Mùa hạ", "Mùa thu", "Mùa đông"},
            {"Summer", "Mùa hạ", "Mùa xuân", "Mùa đông", "Mùa thu"},
            {"Autumn", "Mùa thu", "Mùa xuân", "Mùa hạ", "Mùa đông"},
            {"Winter", "Mùa đông", "Mùa xuân", "Mùa hạ", "Mùa thu"},
            {"Travel", "Du lịch", "Ở nhà", "Làm việc", "Đi học"},
            {"Music", "Âm nhạc", "Hội họa", "Điện ảnh", "Thể thao"},
            {"Art", "Nghệ thuật", "Khoa học", "Toán học", "Kinh tế"},
            {"Science", "Khoa học", "Nghệ thuật", "Lịch sử", "Địa lý"},
            {"History", "Lịch sử", "Tương lai", "Hiện tại", "Khoa học"},
            {"Future", "Tương lai", "Quá khứ", "Hiện tại", "Lịch sử"},
            {"Past", "Quá khứ", "Tương lai", "Hiện tại", "Ngày mai"}
        };

        for (String[] w : basicWords) {
            List<String> opts = new ArrayList<>(Arrays.asList(w[1], w[2], w[3], w[4]));
            Collections.shuffle(opts);
            pool.add(Map.of("word", w[0], "options", opts, "correct", opts.indexOf(w[1])));
        }
        
        Collections.shuffle(pool);
        return pool.stream().limit(10).collect(Collectors.toList());
    }
}

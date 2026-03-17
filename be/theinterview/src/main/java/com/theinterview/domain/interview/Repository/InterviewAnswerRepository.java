package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Integer> {

    // 특정 질문에 대한 답변 조회
    Optional<InterviewAnswer> findByInterviewQuestionId(Integer questionId);

    // 특정 질문에 대한 답변 삭제
    void deleteAllByInterviewQuestionId(Integer questionId);
}

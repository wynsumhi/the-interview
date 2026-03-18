package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Integer> {

    // 특정 인터뷰에 대한 인터뷰 답변 목록을 시퀀스 순서대로 불러옴
    List<InterviewQuestion> findAllByInterviewIdOrderBySequence(Integer interviewId);

    // 특정 인터뷰에 대한 인터뷰 답변 목록을 불러옴
    List<InterviewQuestion> findAllByInterviewId(Integer interviewId);

    // 특정 인터뷰에 대한 인터뷰 답변을 전부 삭제함
    void deleteAllByInterviewId(Integer interviewId);
}

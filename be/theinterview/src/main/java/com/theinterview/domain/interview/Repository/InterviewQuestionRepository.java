package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Integer> {

    List<InterviewQuestion> findAllByInterviewIdOrderBySequence(Integer interviewId);
}

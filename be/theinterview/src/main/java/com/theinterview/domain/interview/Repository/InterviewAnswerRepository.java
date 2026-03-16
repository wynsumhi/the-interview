package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Integer> {

    Optional<InterviewAnswer> findByInterviewQuestionId(Integer questionId);
}

package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Integer> {

}

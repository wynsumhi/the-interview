package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.QuestionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionCandidateRepository extends JpaRepository<QuestionCandidate, Integer> {

}

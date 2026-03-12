package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Integer> {
    
}

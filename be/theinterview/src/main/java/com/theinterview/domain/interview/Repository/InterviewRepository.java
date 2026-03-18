package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.interview.enums.InterviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Integer> {

    Slice<Interview> findAllByMemberIdAndStatus(Integer memberId, InterviewStatus status,
            Pageable pageable);
}

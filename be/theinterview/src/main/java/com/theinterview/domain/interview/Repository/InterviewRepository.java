package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.interview.enums.InterviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Integer> {

    Slice<Interview> findAllByMemberIdAndStatus(Integer memberId, InterviewStatus status,
            Pageable pageable);

    // 완료상태인 인터뷰만 가져옵니다.
    Slice<Interview> findAllByStatus(InterviewStatus status, Pageable pageable);

    // 특정 사용자 memberId를 이용해서 모든 인터뷰를 가져옵니다.
    List<Interview> findAllByMemberId(Integer memberId);

    // 특정 UUID를 가지고 일치하는 인터뷰를 가져옵니다.
    Optional<Interview> findByShareLink(String shareLink);
}

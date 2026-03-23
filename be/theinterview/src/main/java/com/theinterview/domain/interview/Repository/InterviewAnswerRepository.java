package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewAnswer;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Integer> {

    // 특정 질문에 대한 답변 조회
    Optional<InterviewAnswer> findByInterviewQuestionId(Integer questionId);

    // 특정 질문에 대한 답변 삭제
    void deleteAllByInterviewQuestionId(Integer questionId);

    // User의 UUID를 이용해서 인터뷰 답변을 조회하고 해당 답변의 공개 범위가 true인것만 가져옴
    @Query("SELECT ia FROM InterviewAnswer ia " +
            "JOIN FETCH ia.interviewQuestion iq " +
            "JOIN FETCH iq.interview i " +
            "JOIN i.member m " +
            "WHERE m.userUuid = :userUUID AND ia.shareRange = true")
    Slice<InterviewAnswer> findAllByMemberUUIDAndShared(@Param("userUUID") String userUUID,
            Pageable pageable);
}

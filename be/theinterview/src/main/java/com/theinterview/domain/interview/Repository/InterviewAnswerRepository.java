package com.theinterview.domain.interview.Repository;

import com.theinterview.domain.interview.entity.InterviewAnswer;
import java.util.List;
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
    // userUuid로 공유 가능한 답변 포스트잇 목록을 조회한다
    // - 질문, 인터뷰, 작성자 회원까지 함께 fetch 한다
    // - 답변 shareRange 가 true 인 항목만 조회한다
    // - 최신 포스트잇이 먼저 보이도록 createdAt 내림차순 정렬한다
    @Query("SELECT ia FROM InterviewAnswer ia " +
            "JOIN FETCH ia.interviewQuestion iq " +
            "JOIN FETCH iq.interview i " +
            "JOIN FETCH i.member m " +
            "WHERE m.userUuid = :userUUID AND ia.shareRange = true " +
            "ORDER BY ia.createdAt DESC")
    Slice<InterviewAnswer> findAllByMemberUUIDAndShared(@Param("userUUID") String userUUID,
            Pageable pageable);

    // 음원의 UUID를 가지고 답변 조회
    // TODO: 지금 DB에 저장하는 방식이 uuid만 저장하는게 아니기 때문에 LIKE방식이 유일, 검색 속도 매우 느림
    @Query("SELECT ia FROM InterviewAnswer ia WHERE ia.answerAudioUrl LIKE %:voiceUuid%")
    Optional<InterviewAnswer> findByVoiceUuidInPath(@Param("voiceUuid") String voiceUuid);

    @Query("""
            select ia
            from InterviewAnswer ia
            join fetch ia.interviewQuestion iq
            join fetch iq.interview i
            join fetch i.member m
            where ia.id in :answerIds
              and ia.shareRange = true
            """)
    List<InterviewAnswer> findAllSharedDetailByIdIn(@Param("answerIds") List<Integer> answerIds);

    @Query("""
            select ia
            from InterviewAnswer ia
            join fetch ia.interviewQuestion iq
            join fetch iq.interview i
            join fetch i.member m
            where m.userUuid = :userUUID
              and ia.shareRange = true
            """)
    List<InterviewAnswer> findAllSharedDetailByMemberUUID(@Param("userUUID") String userUUID);
}

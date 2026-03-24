package com.theinterview.domain.shareboard.repository;

import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 공유 보드 질문 포스트잇에 대한 JPA Repository
public interface ShareBoardQuestionRepository extends JpaRepository<ShareBoardQuestion, Integer> {

    // 질문 보드 목록 조회용 쿼리
    // - 보드 ID 기준 조회
    // - 삭제되지 않은 질문만 조회
    // - cursor 보다 작은 ID 만 조회해서 커서 페이징을 흉내 낸다
    // - 작성자/보드/시작 인터뷰를 fetch join 으로 함께 가져온다
    @Query("""
            select q
            from ShareBoardQuestion q
            join fetch q.shareBoard sb
            join fetch q.author a
            left join fetch q.startedInterview si
            where sb.id = :shareBoardId
              and q.isDeleted = false
              and (:cursor is null or q.id < :cursor)
            order by q.id desc
            """)
    List<ShareBoardQuestion> findVisibleQuestions(
            @Param("shareBoardId") Integer shareBoardId,
            @Param("cursor") Integer cursor,
            Pageable pageable
    );

    // 질문 단건 상세 조회용 쿼리
    // 권한 검사를 위해 보드 소유자까지 같이 가져온다
    @Query("""
            select q
            from ShareBoardQuestion q
            join fetch q.shareBoard sb
            join fetch sb.owner o
            join fetch q.author a
            left join fetch q.startedInterview si
            where q.id = :questionId
            """)
    Optional<ShareBoardQuestion> findDetailById(@Param("questionId") Integer questionId);
}

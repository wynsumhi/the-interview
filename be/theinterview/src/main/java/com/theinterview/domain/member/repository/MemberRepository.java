package com.theinterview.domain.member.repository;


import com.theinterview.domain.member.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Integer> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);

    // 사용자 공유 보드 조회를 위해 userUuid로 회원을 조회한다
    Optional<Member> findByUserUuid(String userUuid);

}

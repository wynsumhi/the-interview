package com.theinterview.domain.member.repository;


import com.theinterview.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Integer> {

    public boolean existsByEmail(String email);

    public boolean existsByNickname(String nickname);
}

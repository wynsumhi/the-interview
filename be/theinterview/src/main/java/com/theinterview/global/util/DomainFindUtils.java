package com.theinterview.global.util;

import com.theinterview.domain.interview.Repository.InterviewAnswerRepository;
import com.theinterview.domain.interview.Repository.InterviewQuestionRepository;
import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.interview.entity.InterviewAnswer;
import com.theinterview.domain.interview.entity.InterviewQuestion;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 여러 서비스에서 공통으로 사용하는 도메인 조회 유틸입니다.
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DomainFindUtils {

    // 회원 엔티티를 조회하는 저장소입니다.
    private final MemberRepository memberRepository;
    // 인터뷰 엔티티를 조회하는 저장소입니다.
    private final InterviewRepository interviewRepository;
    // 인터뷰 질문 엔티티를 조회하는 저장소입니다.
    private final InterviewQuestionRepository interviewQuestionRepository;
    // 인터뷰 답변 엔티티를 조회하는 저장소입니다.
    private final InterviewAnswerRepository interviewAnswerRepository;

    // member PK로 회원을 조회합니다.
    public Member getMemberById(int memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
    }

    // email로 회원을 조회합니다.
    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
    }

    // interview PK로 인터뷰를 조회합니다.
    public Interview getInterviewById(int interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));
    }

    // interviewQuestion PK로 질문을 조회합니다.
    public InterviewQuestion getInterviewQuestionById(int interviewQuestionId) {
        return interviewQuestionRepository.findById(interviewQuestionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));
    }

    // interviewAnswer PK로 답변을 조회합니다.
    // 공유 보드 레이아웃은 answer PK를 sourceId로 직접 사용하므로 findById가 맞습니다.
    public InterviewAnswer getInterviewAnswerById(int interviewAnswerId) {
        return interviewAnswerRepository.findById(interviewAnswerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.ANSWER_NOT_FOUND));
    }
}

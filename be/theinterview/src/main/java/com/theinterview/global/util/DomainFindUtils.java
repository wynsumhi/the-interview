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

/**
 * 필요한 도메인 객체를 pk를 통해 찾아오는 유틸. member 객체의 경우 email로 조회하는 메서드가 포함됨
 */
@Component
@RequiredArgsConstructor
public class DomainFindUtils {

    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;

    /**
     * pk를 통해 member 도메인 객체를 조회
     */

    public Member getMemberById(int memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
    }

    /**
     * email을 통해 member 도메인 객체 조회
     */
    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
    }

    /**
     * pk를 통해 interview 도메인 객체 조회
     */
    public Interview getInterviewById(int interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));
    }

    /**
     * pk를 통해 interviewQuestion 도메인 객체 조회
     */
    public InterviewQuestion getInterviewQuestionById(int interviewQuestionId) {
        return interviewQuestionRepository.findById(interviewQuestionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));
    }

    /**
     * pk를 통해 interviewAnswer 도메인 객체 조회
     */
    public InterviewAnswer getInterviewAnswerById(int interviewAnswerId) {
        return interviewAnswerRepository.findByInterviewQuestionId(interviewAnswerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.ANSWER_NOT_FOUND));
    }
}

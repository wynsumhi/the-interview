package com.theinterview.domain.member;

import com.theinterview.domain.interview.entity.QuestionCandidate;
import com.theinterview.domain.member.dto.MemberRequest.MemberInfoUpdateRequest;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Integer id;

    @Column(name = "email", nullable = false, length = 50, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, length = 50, unique = true)
    private String nickname;

    @Column(name = "is_first_finished", nullable = false)
    private Boolean isFirstFinished;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "member")
    @OrderBy("createdAt DESC") // 일단 가장 최근의 다음 질문을 불러오도록 설정
    private List<QuestionCandidate> questionCandidates = new ArrayList<>();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Member(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.isFirstFinished = false;
    }

    // 첫 인터뷰가 완료되었다면 호출 할 메서드
    public void completeFirstInterview() {
        this.isFirstFinished = true;
    }

    // 사용자 정보 수정 메서드
    public void changeMemberInfo(MemberInfoUpdateRequest request, PasswordEncoder passwordEncoder) {

        String changeNickname = request.getNickname();
        String changePassword = request.getPassword();

        System.out.println(changeNickname);

        if (changeNickname != null && changeNickname.length() >= 3
                && changeNickname.length() <= 8) {
            this.nickname = changeNickname;
        } else {
            throw new GeneralException(ErrorStatus.NICKNAME_NOT_VALID);
        }

        if (changePassword != null && !changePassword.isBlank()) {
            this.password = passwordEncoder.encode(changePassword);
        }

    }
}

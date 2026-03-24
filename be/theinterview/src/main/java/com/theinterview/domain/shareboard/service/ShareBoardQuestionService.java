package com.theinterview.domain.shareboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.theinterview.domain.interview.Repository.QuestionCandidateRepository;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.entity.QuestionCandidate;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.DeleteShareBoardQuestionResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardInfoResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionItemResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionSliceResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.StartInterviewFromQuestionResponse;
import com.theinterview.domain.shareboard.entity.ShareBoard;
import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import com.theinterview.domain.shareboard.enums.ShareBoardQuestionStatus;
import com.theinterview.domain.shareboard.repository.ShareBoardQuestionRepository;
import com.theinterview.domain.shareboard.repository.ShareBoardRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.util.DomainFindUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 공유 보드 질문 포스트잇 기능의 핵심 비즈니스 로직을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareBoardQuestionService {

    private final ShareBoardRepository shareBoardRepository;
    private final ShareBoardQuestionRepository shareBoardQuestionRepository;
    private final QuestionCandidateRepository questionCandidateRepository;
    private final InterviewFacade interviewFacade;
    private final DomainFindUtils domainFindUtils;

    // 로그인 사용자의 공유 보드를 조회하거나, 없으면 새로 만든다
    @Transactional
    public ShareBoardInfoResponse getOrCreateMyShareBoard(String email) {
        // 이메일로 현재 로그인 사용자 조회
        Member member = domainFindUtils.getMemberByEmail(email);

        // 기존 보드가 있으면 가져오고, 없으면 새 보드를 생성한다
        ShareBoard shareBoard = shareBoardRepository.findByOwnerId(member.getId())
                .orElseGet(() -> shareBoardRepository.save(ShareBoard.builder()
                        .owner(member)
                        .shareRange(true)
                        .build()));

        return ShareBoardInfoResponse.from(shareBoard);
    }

    // 특정 공유 보드에 새 질문 포스트잇을 생성한다
    @Transactional
    public ShareBoardQuestionItemResponse createQuestion(Integer shareBoardId, String content,
            String email) {
        // 요청 사용자 조회
        Member requester = domainFindUtils.getMemberByEmail(email);
        // 보드 존재 여부 및 접근 권한 확인
        ShareBoard shareBoard = getAccessibleShareBoard(shareBoardId, requester);

        // 질문 엔티티 생성 후 저장
        ShareBoardQuestion question = shareBoardQuestionRepository.save(
                ShareBoardQuestion.builder()
                        .shareBoard(shareBoard)
                        .author(requester)
                        .content(content.trim())
                        .build()
        );

        return ShareBoardQuestionItemResponse.from(question, requester.getId());
    }

    // 질문 보드 목록을 커서 기반으로 조회한다
    public ShareBoardQuestionSliceResponse getQuestions(Integer shareBoardId, Integer cursor,
            Integer size, String email) {
        // 요청 사용자 조회
        Member requester = domainFindUtils.getMemberByEmail(email);
        // 보드 존재 여부 및 접근 권한 확인
        ShareBoard shareBoard = getAccessibleShareBoard(shareBoardId, requester);

        // size 보정
        int pageSize = normalizeSize(size);
        // pageSize + 1 개를 조회해서 다음 페이지 존재 여부를 판단한다
        List<ShareBoardQuestion> questions = shareBoardQuestionRepository.findVisibleQuestions(
                shareBoard.getId(), cursor, PageRequest.of(0, pageSize + 1));

        // 초과 조회되었으면 다음 페이지가 있다고 판단
        boolean hasNext = questions.size() > pageSize;
        // 실제 응답에는 요청 수만큼만 담는다
        List<ShareBoardQuestionItemResponse> items = questions.stream()
                .limit(pageSize)
                .map(question -> ShareBoardQuestionItemResponse.from(question, requester.getId()))
                .toList();

        // 다음 페이지가 있으면 마지막 아이템 ID 를 nextCursor 로 사용
        Integer nextCursor = hasNext && !items.isEmpty()
                ? items.get(items.size() - 1).getQuestionId()
                : null;

        // 목록 응답 DTO 반환
        return ShareBoardQuestionSliceResponse.builder()
                .questions(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    // 질문 내용을 수정한다
    @Transactional
    public ShareBoardQuestionItemResponse updateQuestion(Integer questionId, String content,
            String email) {
        // 요청 사용자 조회
        Member requester = domainFindUtils.getMemberByEmail(email);
        // 삭제되지 않은 질문 엔티티 조회
        ShareBoardQuestion question = getActiveQuestion(questionId);

        // 보드 접근 권한 확인
        validateShareBoardAccess(question.getShareBoard(), requester);
        // 작성자 본인인지 확인
        validateQuestionAuthor(question, requester);

        // 이미 인터뷰에 사용된 질문은 수정할 수 없다
        if (question.getStatus() == ShareBoardQuestionStatus.USED) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_ALREADY_USED);
        }

        // 내용 수정
        question.updateContent(content.trim());
        // 수정 결과 반환
        return ShareBoardQuestionItemResponse.from(question, requester.getId());
    }

    // 질문을 삭제 처리한다
    @Transactional
    public DeleteShareBoardQuestionResponse deleteQuestion(Integer questionId, String email) {
        // 요청 사용자 조회
        Member requester = domainFindUtils.getMemberByEmail(email);
        // 삭제되지 않은 질문 엔티티 조회
        ShareBoardQuestion question = getActiveQuestion(questionId);

        // 보드 접근 권한 확인
        validateShareBoardAccess(question.getShareBoard(), requester);

        // 현재 요청 사용자가 보드 주인인지 계산
        boolean isOwner = isBoardOwner(question.getShareBoard(), requester);
        // 현재 요청 사용자가 작성자인지 계산
        boolean isAuthor = question.getAuthor().getId().equals(requester.getId());

        // USED 상태면 보드 주인만 삭제 가능
        if (question.getStatus() == ShareBoardQuestionStatus.USED) {
            if (!isOwner) {
                throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_DELETE_FORBIDDEN);
            }
        // OPEN 상태면 작성자 또는 보드 주인만 삭제 가능
        } else if (!(isOwner || isAuthor)) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_DELETE_FORBIDDEN);
        }

        // 소프트 삭제 처리
        question.markDeleted();

        // 삭제 응답 반환
        return DeleteShareBoardQuestionResponse.builder()
                .questionId(question.getId())
                .deleted(true)
                .build();
    }

    // 질문을 기반으로 인터뷰를 시작한다
    // 이 기능은 보드 주인만 수행할 수 있다
    @Transactional
    public StartInterviewFromQuestionResponse startInterviewFromQuestion(Integer questionId,
            String email) throws JsonProcessingException {
        // 요청 사용자 조회
        Member requester = domainFindUtils.getMemberByEmail(email);
        // 삭제되지 않은 질문 엔티티 조회
        ShareBoardQuestion question = getActiveQuestion(questionId);
        // 해당 질문이 속한 보드 조회
        ShareBoard shareBoard = question.getShareBoard();

        // 보드 접근 권한 확인
        validateShareBoardAccess(shareBoard, requester);

        // 보드 주인이 아니면 인터뷰 시작 불가
        if (!isBoardOwner(shareBoard, requester)) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_START_FORBIDDEN);
        }

        // 이미 사용된 질문으로는 중복 시작 불가
        if (question.getStatus() == ShareBoardQuestionStatus.USED) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_ALREADY_USED);
        }

        // 기존 인터뷰 시작 로직은 QuestionCandidate 를 기반으로 첫 질문 후보를 읽는다
        // 따라서 현재 질문 내용을 QuestionCandidate 로 먼저 저장한다
        questionCandidateRepository.save(QuestionCandidate.builder()
                .member(requester)
                .questionText(question.getContent())
                .build());

        // 기존 인터뷰 시작 흐름 재사용
        WebSocketConnectResponse response = interviewFacade.issueWebSocketId(email).block();
        // 방어 코드: 응답이 비어 있으면 서버 에러로 처리
        if (response == null) {
            throw new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }

        // 생성된 인터뷰와 현재 질문을 연결하고 상태를 USED 로 변경
        question.markUsed(domainFindUtils.getInterviewById(response.getInterviewId()));

        // 인터뷰 시작 응답 반환
        return StartInterviewFromQuestionResponse.builder()
                .sourceQuestionId(question.getId())
                .questionStatus(question.getStatus())
                .questionStatusLabel("INTERVIEW_STARTED")
                .interview(response)
                .build();
    }

    // 페이지 크기를 최소/최대 범위 안으로 보정한다
    private int normalizeSize(Integer size) {
        // null 이거나 1보다 작으면 기본값 20 사용
        if (size == null || size < 1) {
            return 20;
        }
        // 너무 큰 값은 50으로 제한
        return Math.min(size, 50);
    }

    // 보드가 존재하는지 확인하고, 현재 요청 사용자가 접근 가능한지도 함께 검사한다
    private ShareBoard getAccessibleShareBoard(Integer shareBoardId, Member requester) {
        // 보드 ID 로 조회
        ShareBoard shareBoard = shareBoardRepository.findById(shareBoardId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.SHARE_BOARD_NOT_FOUND));
        // 공개 범위에 따른 접근 권한 검사
        validateShareBoardAccess(shareBoard, requester);
        return shareBoard;
    }

    // 보드 접근 권한 검사
    // 현재 구현은 boolean shareRange 이므로 true 면 공개, false 면 비공개로 해석한다
    private void validateShareBoardAccess(ShareBoard shareBoard, Member requester) {
        // 공개 보드면 누구나 접근 가능
        if (Boolean.TRUE.equals(shareBoard.getShareRange())) {
            return;
        }

        // 비공개 보드는 보드 주인만 접근 가능
        if (!isBoardOwner(shareBoard, requester)) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_FORBIDDEN);
        }
    }

    // 질문 수정 권한 확인
    // 수정은 질문 작성자만 가능하다
    private void validateQuestionAuthor(ShareBoardQuestion question, Member requester) {
        if (!question.getAuthor().getId().equals(requester.getId())) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_AUTHOR_MISMATCH);
        }
    }

    // 삭제되지 않은 질문만 조회한다
    // 이미 소프트 삭제된 질문은 없는 것으로 취급한다
    private ShareBoardQuestion getActiveQuestion(Integer questionId) {
        // 권한 검사를 위해 보드/소유자/작성자까지 함께 로딩한 상세 조회 사용
        ShareBoardQuestion question = shareBoardQuestionRepository.findDetailById(questionId)
                .orElseThrow(() -> new GeneralException(
                        ErrorStatus.SHARE_BOARD_QUESTION_NOT_FOUND));

        // 소프트 삭제 상태면 조회 실패와 동일하게 처리
        if (Boolean.TRUE.equals(question.getIsDeleted())
                || question.getStatus() == ShareBoardQuestionStatus.DELETED) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_NOT_FOUND);
        }

        return question;
    }

    // 현재 요청 사용자가 보드 주인인지 판단한다
    private boolean isBoardOwner(ShareBoard shareBoard, Member requester) {
        return shareBoard.getOwner().getId().equals(requester.getId());
    }
}

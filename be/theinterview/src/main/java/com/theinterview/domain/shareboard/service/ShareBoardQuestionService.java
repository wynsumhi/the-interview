package com.theinterview.domain.shareboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.theinterview.domain.interview.Repository.InterviewAnswerRepository;
import com.theinterview.domain.interview.Repository.QuestionCandidateRepository;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.entity.InterviewAnswer;
import com.theinterview.domain.interview.entity.QuestionCandidate;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.shareboard.dto.ShareBoardPostitLayoutRequest.UpdateLayoutRequest;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.BookmarkPostitItemResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.BookmarkPostitSliceResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.DeleteShareBoardQuestionResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardInfoResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionItemResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionSliceResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.StartInterviewFromQuestionResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.UpdateLayoutResponse;
import com.theinterview.domain.shareboard.entity.ShareBoard;
import com.theinterview.domain.shareboard.entity.ShareBoardPostitLayout;
import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import com.theinterview.domain.shareboard.enums.ShareBoardPostitType;
import com.theinterview.domain.shareboard.enums.ShareBoardQuestionStatus;
import com.theinterview.domain.shareboard.repository.ShareBoardPostitLayoutRepository;
import com.theinterview.domain.shareboard.repository.ShareBoardQuestionRepository;
import com.theinterview.domain.shareboard.repository.ShareBoardRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.util.DomainFindUtils;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ShareBoardQuestionService {

    // 무한 스크롤에서 한 번에 내려줄 포스트잇 개수입니다.
    private static final int PAGE_SIZE = 12;
    private static final Random RANDOM = new Random();
    // 포스트잇 색상은 사용자가 선택하지 않고 시스템 팔레트에서 자동 지정합니다.
    private static final List<String> COLOR_PALETTE = List.of(
            "#F3C5C5",
            "#B7D4BB",
            "#FFF3A6",
            "#BCCEF6",
            "#FBCDB6"
    );


    // 공유 보드 메타데이터를 조회하는 저장소입니다.
    private final ShareBoardRepository shareBoardRepository;
    // 질문 포스트잇 원본 데이터를 관리하는 저장소입니다.
    private final ShareBoardQuestionRepository shareBoardQuestionRepository;
    // 포스트잇 레이아웃 데이터를 관리하는 저장소입니다.
    private final ShareBoardPostitLayoutRepository shareBoardPostitLayoutRepository;
    // 북마크 탭의 원본인 인터뷰 답변을 조회하는 저장소입니다.
    private final InterviewAnswerRepository interviewAnswerRepository;
    // 질문 기반 인터뷰 시작 시 후보 질문을 저장하는 저장소입니다.
    private final QuestionCandidateRepository questionCandidateRepository;
    // 질문에서 인터뷰를 시작할 때 기존 인터뷰 생성 흐름을 재사용하는 파사드입니다.
    private final InterviewFacade interviewFacade;
    // 공통 도메인 조회 유틸입니다.
    private final DomainFindUtils domainFindUtils;

    public ShareBoardInfoResponse getMyShareBoard(String email) {
        // 로그인 사용자의 본인 공유 보드만 반환합니다.
        Member requester = domainFindUtils.getMemberByEmail(email);
        ShareBoard shareBoard = shareBoardRepository.findByOwnerId(requester.getId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.SHARE_BOARD_NOT_FOUND));
        return ShareBoardInfoResponse.from(shareBoard, requester.getId());
    }

    public ShareBoardInfoResponse getBoardInfoByUserUuid(String userUuid, String email) {
        // URL의 userUuid로 보드를 찾고, 로그인 상태면 owner 여부도 계산합니다.
        ShareBoard shareBoard = getShareBoardByUserUuid(userUuid);
        return ShareBoardInfoResponse.from(shareBoard, getRequesterId(email));
    }

    @Transactional
    public ShareBoardQuestionItemResponse createQuestionByUserUuid(String userUuid, String content,
            String email) {
        // 질문 작성자는 현재 로그인 사용자로 기록합니다.
        Member requester = domainFindUtils.getMemberByEmail(email);
        ShareBoard shareBoard = getShareBoardByUserUuid(userUuid);

        // 질문 포스트잇 원본 데이터를 먼저 생성합니다.
        ShareBoardQuestion question = shareBoardQuestionRepository.save(
                ShareBoardQuestion.builder()
                        .shareBoard(shareBoard)
                        .author(requester)
                        .content(content.trim())
                        .build()
        );

        // 새 질문에는 기본 레이아웃도 동시에 생성합니다.
        ShareBoardPostitLayout layout = createDefaultLayout(shareBoard,
                ShareBoardPostitType.QUESTION,
                question.getId());
        return ShareBoardQuestionItemResponse.from(question, requester.getId(), layout);
    }

    public BookmarkPostitSliceResponse getBookmarkPostits(String userUuid, Integer page,
            String email) {
        ShareBoard shareBoard = getShareBoardByUserUuid(userUuid);
        Integer requesterId = getRequesterId(email);
        // 공유 중인 북마크 답변에 대응하는 레이아웃이 모두 존재하도록 먼저 보정합니다.
        syncBookmarkLayouts(userUuid, shareBoard);

        // 북마크 탭은 레이아웃 updatedAt 기준 최신순으로 조회합니다.
        Pageable pageable = PageRequest.of(normalizePage(page), PAGE_SIZE,
                Sort.by("updatedAt").descending());
        Slice<ShareBoardPostitLayout> layouts =
                shareBoardPostitLayoutRepository.findAllByShareBoardIdAndPostitTypeOrderByUpdatedAtDesc(
                        shareBoard.getId(), ShareBoardPostitType.BOOKMARK, pageable);

        // 레이아웃의 sourceId를 기반으로 실제 답변 데이터를 한 번에 조회합니다.
        List<Integer> sourceIds = layouts.getContent().stream()
                .map(ShareBoardPostitLayout::getSourceId)
                .toList();
        Map<Integer, InterviewAnswer> answerMap = sourceIds.isEmpty()
                ? new HashMap<>()
                : interviewAnswerRepository.findAllSharedDetailByIdIn(sourceIds)
                        .stream()
                        .collect(HashMap::new, (map, answer) -> map.put(answer.getId(), answer),
                                HashMap::putAll);

        // BOOKMARK 레이아웃은 보드 주인만 수정할 수 있습니다.
        boolean canEditLayout = isBoardOwner(shareBoard, requesterId);
        List<BookmarkPostitItemResponse> items = layouts.getContent().stream()
                .filter(layout -> answerMap.containsKey(layout.getSourceId()))
                .map(layout -> BookmarkPostitItemResponse.from(answerMap.get(layout.getSourceId()),
                        layout, canEditLayout))
                .toList();

        return BookmarkPostitSliceResponse.builder()
                .ownerUserUuid(shareBoard.getOwner().getUserUuid())
                .items(items)
                .currentPage(normalizePage(page))
                .hasNext(layouts.hasNext())
                .build();
    }

    public ShareBoardQuestionSliceResponse getQuestionPostits(String userUuid, Integer page,
            String email) {
        ShareBoard shareBoard = getShareBoardByUserUuid(userUuid);
        Integer requesterId = getRequesterId(email);
        // 질문 탭은 질문 updatedAt 기준 최신순으로 조회합니다.
        Pageable pageable = PageRequest.of(normalizePage(page), PAGE_SIZE,
                Sort.by("updatedAt").descending());

        Slice<ShareBoardQuestion> questions =
                shareBoardQuestionRepository.findAllByShareBoardIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        shareBoard.getId(), pageable);

        List<Integer> questionIds = questions.getContent().stream()
                .map(ShareBoardQuestion::getId)
                .toList();
        Map<Integer, ShareBoardPostitLayout> layoutMap = questionIds.isEmpty()
                ? new HashMap<>()
                : shareBoardPostitLayoutRepository
                        .findAllByShareBoardIdAndPostitTypeAndSourceIdIn(shareBoard.getId(),
                                ShareBoardPostitType.QUESTION, questionIds)
                        .stream()
                        .collect(HashMap::new,
                                (map, layout) -> map.put(layout.getSourceId(), layout),
                                HashMap::putAll);

        // 질문 원본과 레이아웃을 합쳐 프론트 응답 DTO로 변환합니다.
        List<ShareBoardQuestionItemResponse> items = questions.getContent().stream()
                .map(question -> ShareBoardQuestionItemResponse.from(question, requesterId,
                        layoutMap.get(question.getId())))
                .toList();

        return ShareBoardQuestionSliceResponse.builder()
                .ownerUserUuid(shareBoard.getOwner().getUserUuid())
                .items(items)
                .currentPage(normalizePage(page))
                .hasNext(questions.hasNext())
                .build();
    }

    @Transactional
    public UpdateLayoutResponse updateLayout(String userUuid, ShareBoardPostitType postitType,
            Integer sourceId, UpdateLayoutRequest request, String email) {
        Member requester = domainFindUtils.getMemberByEmail(email);
        ShareBoard shareBoard = getShareBoardByUserUuid(userUuid);
        // 레이아웃이 없으면 기본 레이아웃을 만든 뒤 수정합니다.
        ShareBoardPostitLayout layout = getOrCreateLayout(shareBoard, postitType, sourceId);

        // 보드 주인 또는 질문 작성자인지 정책에 따라 수정 권한을 검사합니다.
        boolean canEditLayout = canEditLayout(shareBoard, requester.getId(), postitType, sourceId);
        if (!canEditLayout) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        // 색상은 고정값이므로 회전, 좌표, z-index만 수정합니다.
        layout.updatePlacement(toDecimal(request.getRotationDeg()),
                toDecimal(request.getPositionX()),
                toDecimal(request.getPositionY()), request.getZIndex());

        return UpdateLayoutResponse.from(postitType, sourceId, layout, true);
    }

    @Transactional
    public ShareBoardQuestionItemResponse updateQuestion(Integer questionId, String content,
            String email) {
        Member requester = domainFindUtils.getMemberByEmail(email);
        ShareBoardQuestion question = getActiveQuestion(questionId);

        // 질문 내용은 작성자만 수정할 수 있습니다.
        validateQuestionAuthor(question, requester);
        if (question.getStatus() == ShareBoardQuestionStatus.USED) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_ALREADY_USED);
        }

        // 응답에 포함할 레이아웃이 누락되지 않도록 함께 보장합니다.
        question.updateContent(content.trim());
        ShareBoardPostitLayout layout = getOrCreateLayout(question.getShareBoard(),
                ShareBoardPostitType.QUESTION, question.getId());
        return ShareBoardQuestionItemResponse.from(question, requester.getId(), layout);
    }

    @Transactional
    public DeleteShareBoardQuestionResponse deleteQuestion(Integer questionId, String email) {
        Member requester = domainFindUtils.getMemberByEmail(email);
        ShareBoardQuestion question = getActiveQuestion(questionId);

        // USED 질문은 owner만 삭제 가능하고, 그 외에는 owner 또는 author가 삭제할 수 있습니다.
        boolean isOwner = isBoardOwner(question.getShareBoard(), requester.getId());
        boolean isAuthor = question.getAuthor().getId().equals(requester.getId());

        if (question.getStatus() == ShareBoardQuestionStatus.USED) {
            if (!isOwner) {
                throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_DELETE_FORBIDDEN);
            }
        } else if (!(isOwner || isAuthor)) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_DELETE_FORBIDDEN);
        }

        // 질문 원본이 삭제되면 연결된 QUESTION 레이아웃도 함께 삭제합니다.
        question.markDeleted();
        shareBoardPostitLayoutRepository.deleteByShareBoardIdAndPostitTypeAndSourceId(
                question.getShareBoard().getId(), ShareBoardPostitType.QUESTION, question.getId());

        return DeleteShareBoardQuestionResponse.builder()
                .questionId(question.getId())
                .deleted(true)
                .build();
    }

    @Transactional
    public StartInterviewFromQuestionResponse startInterviewFromQuestion(Integer questionId,
            String email) throws JsonProcessingException {
        Member requester = domainFindUtils.getMemberByEmail(email);
        ShareBoardQuestion question = getActiveQuestion(questionId);
        ShareBoard shareBoard = question.getShareBoard();

        // 질문 기반 인터뷰 시작은 보드 주인에게만 허용합니다.
        if (!isBoardOwner(shareBoard, requester.getId())) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_START_FORBIDDEN);
        }

        if (question.getStatus() == ShareBoardQuestionStatus.USED) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_ALREADY_USED);
        }

        questionCandidateRepository.save(QuestionCandidate.builder()
                .member(requester)
                .questionText(question.getContent())
                .build());

        // 기존 인터뷰 생성 흐름을 재사용하기 위해 파사드를 호출합니다.
        WebSocketConnectResponse response = interviewFacade.issueWebSocketId(email, "Jessica",
                        false, null)
                .block();
        if (response == null) {
            throw new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }

        question.markUsed(domainFindUtils.getInterviewById(response.getInterviewId()));

        return StartInterviewFromQuestionResponse.builder()
                .sourceQuestionId(question.getId())
                .questionStatus(question.getStatus())
                .questionStatusLabel("INTERVIEW_STARTED")
                .interview(response)
                .build();
    }

    @Transactional
    public void ensureBookmarkLayout(InterviewAnswer answer) {
        // 북마크 원본 답변의 주인을 기준으로 연결될 공유 보드를 찾습니다.
        ShareBoard shareBoard = shareBoardRepository.findByOwnerId(
                        answer.getInterviewQuestion().getInterview().getMember().getId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.SHARE_BOARD_NOT_FOUND));

        // 이미 레이아웃이 있으면 유지하고, 없으면 기본 레이아웃을 생성합니다.
        shareBoardPostitLayoutRepository
                .findByShareBoardIdAndPostitTypeAndSourceId(shareBoard.getId(),
                        ShareBoardPostitType.BOOKMARK, answer.getId())
                .orElseGet(
                        () -> shareBoardPostitLayoutRepository.save(ShareBoardPostitLayout.builder()
                                .shareBoard(shareBoard)
                                .postitType(ShareBoardPostitType.BOOKMARK)
                                .sourceId(answer.getId())
                                .colorCode(
                                        resolveColor())
                                .rotationDeg(defaultRotation(answer.getId()))
                                .positionX(defaultPositionX(answer.getId()))
                                .positionY(defaultPositionY(answer.getId()))
                                .zIndex(0)
                                .build()));
    }

    private ShareBoard getShareBoardByUserUuid(String userUuid) {
        // 공유 URL에 들어 있는 값은 member.userUuid이므로 owner 기준으로 보드를 찾습니다.
        return shareBoardRepository.findByOwnerUserUuid(userUuid)
                .orElseThrow(() -> new GeneralException(ErrorStatus.SHARE_BOARD_NOT_FOUND));
    }

    private Integer getRequesterId(String email) {
        // 비로그인 공개 조회일 수 있으므로 email이 없으면 null을 반환합니다.
        if (email == null) {
            return null;
        }
        return domainFindUtils.getMemberByEmail(email).getId();
    }

    private int normalizePage(Integer page) {
        // 잘못된 page 값은 첫 페이지로 보정합니다.
        return page == null || page < 0 ? 0 : page;
    }

    private boolean isBoardOwner(ShareBoard shareBoard, Integer requesterMemberId) {
        // 요청 사용자가 현재 공유 보드의 owner인지 판별합니다.
        return requesterMemberId != null
                && shareBoard.getOwner().getId().equals(requesterMemberId);
    }

    private void validateQuestionAuthor(ShareBoardQuestion question, Member requester) {
        // 질문 수정은 질문 작성자 본인만 허용합니다.
        if (!question.getAuthor().getId().equals(requester.getId())) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_AUTHOR_MISMATCH);
        }
    }

    private ShareBoardQuestion getActiveQuestion(Integer questionId) {
        // 삭제되지 않은 질문만 유효한 질문으로 취급합니다.
        ShareBoardQuestion question = shareBoardQuestionRepository.findDetailById(questionId)
                .orElseThrow(() -> new GeneralException(
                        ErrorStatus.SHARE_BOARD_QUESTION_NOT_FOUND));

        if (Boolean.TRUE.equals(question.getIsDeleted())
                || question.getStatus() == ShareBoardQuestionStatus.DELETED) {
            throw new GeneralException(ErrorStatus.SHARE_BOARD_QUESTION_NOT_FOUND);
        }

        return question;
    }

    private void syncBookmarkLayouts(String userUuid, ShareBoard shareBoard) {
        // 현재 공유 중인 북마크 답변 전체를 순회하며 레이아웃 누락을 보정합니다.
        List<InterviewAnswer> sharedAnswers = interviewAnswerRepository.findAllSharedDetailByMemberUUID(
                userUuid);
        sharedAnswers.forEach(this::ensureBookmarkLayout);
    }

    private ShareBoardPostitLayout createDefaultLayout(ShareBoard shareBoard,
            ShareBoardPostitType postitType, Integer sourceId) {
        // 새 포스트잇에는 시스템이 지정한 기본 색상과 기본 위치를 적용합니다.
        return shareBoardPostitLayoutRepository.save(ShareBoardPostitLayout.builder()
                .shareBoard(shareBoard)
                .postitType(postitType)
                .sourceId(sourceId)
                .colorCode(resolveColor())
                .rotationDeg(defaultRotation(sourceId))
                .positionX(defaultPositionX(sourceId))
                .positionY(defaultPositionY(sourceId))
                .zIndex(0)
                .build());
    }

    private ShareBoardPostitLayout getOrCreateLayout(ShareBoard shareBoard,
            ShareBoardPostitType postitType, Integer sourceId) {
        // 레이아웃이 없으면 원본 데이터 존재 여부를 확인한 뒤 기본 레이아웃을 생성합니다.
        return shareBoardPostitLayoutRepository
                .findByShareBoardIdAndPostitTypeAndSourceId(shareBoard.getId(), postitType,
                        sourceId)
                .orElseGet(() -> {
                    if (postitType == ShareBoardPostitType.QUESTION) {
                        getActiveQuestion(sourceId);
                        createDefaultLayout(shareBoard, postitType, sourceId);
                    } else {
                        ensureBookmarkLayout(domainFindUtils.getInterviewAnswerById(sourceId));
                    }
                    return shareBoardPostitLayoutRepository
                            .findByShareBoardIdAndPostitTypeAndSourceId(shareBoard.getId(),
                                    postitType, sourceId)
                            .orElseThrow(() -> new GeneralException(
                                    ErrorStatus.INTERNAL_SERVER_ERROR));
                });
    }

    private boolean canEditLayout(ShareBoard shareBoard, Integer requesterId,
            ShareBoardPostitType postitType, Integer sourceId) {
        // 보드 주인은 모든 포스트잇 레이아웃을 수정할 수 있습니다.
        if (isBoardOwner(shareBoard, requesterId)) {
            return true;
        }

        // QUESTION이 아니거나 비로그인 사용자는 추가 수정 권한이 없습니다.
        if (postitType != ShareBoardPostitType.QUESTION || requesterId == null) {
            return false;
        }

        // 질문 작성자는 자기 QUESTION 레이아웃만 수정할 수 있습니다.
        ShareBoardQuestion question = getActiveQuestion(sourceId);
        return question.getAuthor().getId().equals(requesterId);
    }

    // 색상을 랜덤으로 지정
    private String resolveColor() {
        return COLOR_PALETTE.get(RANDOM.nextInt(COLOR_PALETTE.size()));
    }

    private BigDecimal defaultRotation(Integer sourceId) {
        // 기본 회전값은 -3으로 시작합니다.
        return BigDecimal.valueOf(-3);
    }

    private BigDecimal defaultPositionX(Integer sourceId) {
        // 기본 X 좌표는 0으로 시작합니다.
        return BigDecimal.ZERO;
    }

    private BigDecimal defaultPositionY(Integer sourceId) {
        // 기본 Y 좌표는 0으로 시작합니다.
        return BigDecimal.ZERO;
    }

    private BigDecimal toDecimal(Double value) {
        return BigDecimal.valueOf(value);
    }

    private BigDecimal toDecimal(Integer value) {
        return BigDecimal.valueOf(value);
    }
}

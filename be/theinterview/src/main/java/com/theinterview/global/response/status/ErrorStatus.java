package com.theinterview.global.response.status;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorStatus {
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "서버 에러, 관리자에게 문의 바랍니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "401", "로그인 인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "403", "금지된 요청입니다."),
    MEMBER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "400", "사용자가 이미 존재합니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 사용자를 찾을 수 없습니다."),
    SHARE_BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 공유 보드를 찾을 수 없습니다."),
    SHARE_BOARD_FORBIDDEN(HttpStatus.FORBIDDEN, "403", "해당 공유 보드에 접근할 수 없습니다."),
    SHARE_BOARD_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 공유 보드 질문을 찾을 수 없습니다."),
    SHARE_BOARD_QUESTION_AUTHOR_MISMATCH(HttpStatus.FORBIDDEN, "403", "질문 작성자만 수정할 수 있습니다."),
    SHARE_BOARD_QUESTION_ALREADY_USED(HttpStatus.CONFLICT, "409", "이미 인터뷰 시작에 사용된 질문입니다."),
    SHARE_BOARD_QUESTION_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "403", "질문을 삭제할 권한이 없습니다."),
    SHARE_BOARD_QUESTION_START_FORBIDDEN(HttpStatus.FORBIDDEN, "403",
            "보드 주인만 이 질문으로 인터뷰를 시작할 수 있습니다."),
    NICKNAME_NOT_VALID(HttpStatus.BAD_REQUEST, "400", "닉네임이 유효하지 않습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 질문을 찾을 수 없습니다"),
    INTERVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 인터뷰를 찾을 수 없습니다"),
    QUESTION_NOT_SAVED(HttpStatus.CONFLICT, "409", "아직 이전 인터뷰가 저장 완료되지 않았습니다."),
    ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 답변을 찾을 수 없습니다"),
    EMPTY_AUDIO_FILE(HttpStatus.BAD_REQUEST, "400", "답변이 녹음되지 않았습니다 다시 시도해주세요."),
    SHARE_NOT_ENABLED(HttpStatus.NOT_FOUND, "403", "공개 범위가 비공개 입니다."),
    VOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 음원을 찾을 수 없습니다"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "알림을 찾을 수 없습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

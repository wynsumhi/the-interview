# Share Board Question API Draft

## 1. 설계 원칙

- 질문 리소스는 공유 보드에 종속된다.
- 질문 작성/수정/삭제는 인증이 필요하다.
- 수정 권한은 질문 작성자에게만 있다.
- 삭제 권한은 질문 작성자와 보드 주인에게 있다.
- 공유 보드 접근 권한이 있는 사용자는 질문 보드를 조회할 수 있다.
- 보드 주인은 자신의 보드에 달린 질문을 관리할 수 있다.
- `USED` 상태 질문은 작성자가 수정/삭제할 수 없고, 보드 주인만 삭제할 수 있다.

## 2. 리소스 구조 제안

- `ShareBoard`
- `ShareBoardQuestion`

질문은 공유 보드 하위의 질문 보드 리소스로 해석하는 방식을 권장한다.

공유 화면 개념:

- 북마크 보드: 인터뷰에서 북마크한 Q&A 포스트잇
- 질문 보드: 방문자가 남긴 질문 포스트잇

## 3. 엔드포인트 목록 초안

### 3.1 질문 생성

`POST /api/v1/share-boards/{shareBoardId}/questions`

요청 본문:

```json
{
  "content": "당신이 가장 오래 붙잡고 있었던 감정은 무엇이었나요?"
}
```

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON201",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 31,
    "shareBoardId": 8,
    "author": {
      "memberId": 102,
      "nickname": "mira"
    },
    "content": "당신이 가장 오래 붙잡고 있었던 감정은 무엇이었나요?",
    "status": "OPEN",
    "createdAt": "2026-03-24T13:20:00",
    "updatedAt": "2026-03-24T13:20:00",
    "isMine": true
  }
}
```

검증:

- 로그인 여부
- 보드 접근 권한
- 질문 길이
- 빈 문자열 여부

### 3.2 질문 목록 조회

`GET /api/v1/share-boards/{shareBoardId}/questions`

쿼리 파라미터 예시:

- `cursor`
- `size`
- `sort=latest`

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questions": [
      {
        "questionId": 31,
        "shareBoardId": 8,
        "author": {
          "memberId": 102,
          "nickname": "mira"
        },
        "content": "당신이 가장 오래 붙잡고 있었던 감정은 무엇이었나요?",
        "status": "USED",
        "statusLabel": "인터뷰 진행됨",
        "startedInterviewId": 204,
        "createdAt": "2026-03-24T13:20:00",
        "updatedAt": "2026-03-24T13:20:00",
        "isMine": false
      }
    ],
    "nextCursor": 31,
    "hasNext": false
  }
}
```

메모:

- 공유 보드 접근 권한이 있는 방문자는 모두 질문 보드를 조회할 수 있다.
- 따라서 질문 보드는 공개 댓글 목록과 유사한 조회 정책을 가진다.

### 3.3 질문 수정

`PATCH /api/v1/share-board-questions/{questionId}`

요청 본문:

```json
{
  "content": "지금의 당신을 가장 크게 바꾼 장면은 무엇인가요?"
}
```

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 31,
    "content": "지금의 당신을 가장 크게 바꾼 장면은 무엇인가요?",
    "status": "OPEN",
    "updatedAt": "2026-03-24T13:35:00"
  }
}
```

권한:

- 질문 작성자만 가능

제한:

- `USED` 상태면 수정 불가

### 3.4 질문 삭제

`DELETE /api/v1/share-board-questions/{questionId}`

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 31,
    "deleted": true
  }
}
```

권한:

- 질문 작성자 가능
- 보드 주인 가능

제한:

- `USED` 상태면 보드 주인만 삭제 가능

### 3.5 인터뷰 시작용 질문 선택

`POST /api/v1/share-board-questions/{questionId}/select`

설명:

- 보드 주인이 질문을 인터뷰 시작 질문으로 선택한다.
- 선택 즉시 인터뷰 생성까지 할지, 선택만 할지는 분리 가능하다.

요청 본문 예시:

```json
{
  "startInterview": true
}
```

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 31,
    "status": "USED",
    "statusLabel": "인터뷰 진행됨",
    "startedInterviewId": 204
  }
}
```

메모:

- 이 API는 이번 범위에서 필수는 아니지만, 질문과 인터뷰를 연결하려면 명세에 포함해두는 편이 좋다.

## 4. 에러 코드 초안

### 4.1 공통

- `401 UNAUTHORIZED`: 로그인 필요
- `403 FORBIDDEN`: 접근 권한 없음 또는 작성자 아님
- `404 NOT_FOUND`: 보드 또는 질문 없음
- `400 BAD_REQUEST`: 잘못된 입력값

### 4.2 도메인 전용

- `SHARE_BOARD_NOT_ACCESSIBLE`
- `QUESTION_CONTENT_EMPTY`
- `QUESTION_CONTENT_TOO_LONG`
- `QUESTION_ALREADY_USED`
- `QUESTION_AUTHOR_MISMATCH`

## 5. 응답 필드 권장안

질문 응답 객체:

```json
{
  "questionId": 31,
  "shareBoardId": 8,
  "author": {
    "memberId": 102,
    "nickname": "mira"
  },
  "content": "질문 내용",
  "status": "OPEN",
  "statusLabel": "대기 중",
  "startedInterviewId": null,
  "createdAt": "2026-03-24T13:20:00",
  "updatedAt": "2026-03-24T13:20:00",
  "isMine": false
}
```

## 6. Open Questions

1. 질문 선택 API를 별도 둘지, 기존 인터뷰 생성 API에 통합할지

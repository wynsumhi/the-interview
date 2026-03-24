# Share Board Question API Spec Final

## 1. 공통

Base Path:

`/api/v1`

공통 응답 형식:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {}
}
```

주의:

- 현재 프로젝트의 `ApiResponse.onSuccess(...)`는 성공 시 항상 `code: "200"`을 사용한다.
- 따라서 생성 API라도 별도 `201` 코드를 쓰려면 글로벌 응답 정책 추가가 필요하다.

인증:

- 로그인 사용자 기준

상태값:

- `OPEN`
- `USED`
- `DELETED`

표시 라벨:

- `OPEN` -> `대기 중`
- `USED` -> `인터뷰 진행됨`

## 2. 질문 생성

### Endpoint

`POST /api/v1/share-board/{shareBoardId}/questions`

### Path Params

- `shareBoardId`: 질문을 남길 공유 보드 ID

### Request Body

```json
{
  "content": "당신이 요즘 가장 자주 떠올리는 장면은 무엇인가요?"
}
```

### Validation

- `content` 필수
- 공백만 입력 불가
- 최대 길이 정책 필요

### Success Response

```json
{
  "isSuccess": true,
  "code": "200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 31,
    "shareBoardId": 8,
    "author": {
      "memberId": 102,
      "nickname": "mira"
    },
    "content": "당신이 요즘 가장 자주 떠올리는 장면은 무엇인가요?",
    "status": "OPEN",
    "statusLabel": "대기 중",
    "startedInterviewId": null,
    "createdAt": "2026-03-24T13:20:00",
    "updatedAt": "2026-03-24T13:20:00",
    "isMine": true,
    "canEdit": true,
    "canDelete": true
  }
}
```

## 3. 질문 보드 목록 조회

### Endpoint

`GET /api/v1/share-board/{shareBoardId}/questions?cursor=31&size=20`

### Path Params

- `shareBoardId`: 질문 보드를 조회할 공유 보드 ID

### Query Params

- `cursor`: 마지막 조회 기준 ID 또는 커서 값
- `size`: 조회 개수

### Success Response

```json
{
  "isSuccess": true,
  "code": "200",
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
        "content": "당신이 요즘 가장 자주 떠올리는 장면은 무엇인가요?",
        "status": "USED",
        "statusLabel": "인터뷰 진행됨",
        "startedInterviewId": 204,
        "createdAt": "2026-03-24T13:20:00",
        "updatedAt": "2026-03-24T14:05:00",
        "isMine": false,
        "canEdit": false,
        "canDelete": false
      },
      {
        "questionId": 30,
        "shareBoardId": 8,
        "author": {
          "memberId": 115,
          "nickname": "juno"
        },
        "content": "최근의 당신을 설명하는 감정은 무엇인가요?",
        "status": "OPEN",
        "statusLabel": "대기 중",
        "startedInterviewId": null,
        "createdAt": "2026-03-24T12:10:00",
        "updatedAt": "2026-03-24T12:10:00",
        "isMine": true,
        "canEdit": true,
        "canDelete": true
      }
    ],
    "nextCursor": 30,
    "hasNext": true
  }
}
```

### Notes

- 질문 보드는 공유 보드 접근 권한이 있는 모든 방문자에게 공개
- `canEdit`, `canDelete`는 프론트 버튼 제어에 사용 가능

## 4. 질문 수정

### Endpoint

`PATCH /api/v1/share-board/question/{questionId}`

### Path Params

- `questionId`: 수정할 질문 ID

### Request Body

```json
{
  "content": "지금의 당신을 가장 크게 바꾼 경험은 무엇인가요?"
}
```

### Authorization

- 질문 작성자만 가능

### Restriction

- `USED` 상태 질문은 수정 불가

### Success Response

```json
{
  "isSuccess": true,
  "code": "200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 30,
    "content": "지금의 당신을 가장 크게 바꾼 경험은 무엇인가요?",
    "status": "OPEN",
    "statusLabel": "대기 중",
    "updatedAt": "2026-03-24T14:20:00",
    "canEdit": true,
    "canDelete": true
  }
}
```

## 5. 질문 삭제

### Endpoint

`DELETE /api/v1/share-board/question/{questionId}`

### Path Params

- `questionId`: 삭제할 질문 ID

### Authorization

- `OPEN`: 질문 작성자 또는 보드 주인
- `USED`: 보드 주인만 가능

### Success Response

```json
{
  "isSuccess": true,
  "code": "200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 30,
    "deleted": true
  }
}
```

## 6. 질문 기반 인터뷰 시작

### Endpoint

`POST /api/v1/share-board/question/{questionId}/start-interview`

### Path Params

- `questionId`: 인터뷰 시작에 사용할 질문 ID

### Authorization

- 보드 주인만 가능

### Request Body

```json
{
  "useAsOpeningQuestion": true
}
```

### Success Response

```json
{
  "isSuccess": true,
  "code": "200",
  "message": "요청에 성공했습니다.",
  "result": {
    "questionId": 31,
    "status": "USED",
    "statusLabel": "인터뷰 진행됨",
    "startedInterviewId": 204,
    "interviewStartQuestion": "당신이 요즘 가장 자주 떠올리는 장면은 무엇인가요?"
  }
}
```

## 7. 에러 명세

### Common

- `401 UNAUTHORIZED`: 로그인 필요
- `403 FORBIDDEN`: 권한 없음
- `404 NOT_FOUND`: 보드 또는 질문 없음
- `400 BAD_REQUEST`: 요청값 오류

### Domain

현재 프로젝트는 `ErrorStatus` enum에서 실제 응답 `code`를 문자열 숫자로 관리한다.

따라서 신규 에러도 아래처럼 enum 이름과 HTTP 상태를 정의하고, 응답의 `code` 필드는 숫자 문자열로 맞추는 것이 기존 방식과 일치한다.

- `SHARE_BOARD_NOT_ACCESSIBLE` / `403`
- `QUESTION_CONTENT_EMPTY` / `400`
- `QUESTION_CONTENT_TOO_LONG` / `400`
- `QUESTION_AUTHOR_MISMATCH` / `403`
- `QUESTION_ALREADY_USED` / `409`
- `QUESTION_DELETE_FORBIDDEN` / `403`
- `QUESTION_START_FORBIDDEN` / `403`

## 8. 권한 규칙 요약

### 질문 수정

- 작성자만 가능
- `USED` 상태면 불가

### 질문 삭제

- `OPEN`: 작성자 또는 보드 주인
- `USED`: 보드 주인만 가능

### 질문 조회

- 공유 보드 접근 가능한 모든 방문자 가능

### 인터뷰 시작

- 보드 주인만 가능

## 9. 응답 DTO 권장안

### Question Item

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
  "isMine": false,
  "canEdit": false,
  "canDelete": false
}
```

## 10. 구현 메모

- 삭제는 소프트 삭제 권장
- 목록 조회에서는 `DELETED` 제외
- 인터뷰 시작 성공 시 질문 상태 `USED` 갱신 필요
- 프론트는 `statusLabel`, `canEdit`, `canDelete`를 그대로 활용 가능
- ID 타입은 현재 코드베이스 관례에 맞춰 `Integer` 기준으로 구현하는 편이 자연스럽다

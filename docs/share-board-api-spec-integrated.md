# 공유 보드 API 명세서

## 1. 문서 기준

이 문서는 [docs/share-board-integrated-design.md](c:/Users/SSAFY/Desktop/S14P21A307/docs/share-board-integrated-design.md) 기준으로 작성한 API 명세서다.

전제:

- 공유 보드는 회원가입 시 자동 생성된다.
- 공유 보드 URL은 보드 주인의 `userUuid` 기준이다.
- 보드 주인과 방문자는 동일 URL `/share-board/{userUuid}` 를 사용한다.
- `userUuid`를 알면 누구나 해당 보드에 접근할 수 있다.
- 포스트잇 탭은 `Bookmark`, `Question` 2개다.
- 색상은 시스템이 지정하며 사용자 수정 대상이 아니다.
- 보드 주인은 모든 포스트잇 레이아웃 수정 가능
- 질문 작성자는 자신의 `QUESTION` 포스트잇 레이아웃만 수정 가능


## 2. 공통 규칙

### 인증

- 보드 조회 API는 `userUuid`를 알면 누구나 접근 가능하다는 전제로 설계한다.
- 질문 생성, 질문 수정/삭제, 레이아웃 수정, 인터뷰 시작은 인증 필요

### 응답 형식

아래 예시는 실제 `ApiResponse` wrapper 내부의 `result` 기준으로 작성한다.

### 식별자 규칙

- 보드 접근: `userUuid`
- 질문 단건 제어: `questionId`
- 레이아웃 수정 대상: `userUuid + postitType + sourceId`

### 포스트잇 타입

- `BOOKMARK`
- `QUESTION`


## 3. API 목록

- `GET /v1/share-board/member/{userUuid}`
- `GET /v1/share-board/member/{userUuid}/postits/bookmarks`
- `GET /v1/share-board/member/{userUuid}/postits/questions`
- `POST /v1/share-board/member/{userUuid}/questions`
- `PATCH /v1/share-board/question/{questionId}`
- `DELETE /v1/share-board/question/{questionId}`
- `POST /v1/share-board/question/{questionId}/start-interview`
- `PATCH /v1/share-board/member/{userUuid}/postits/{postitType}/{sourceId}/layout`


## 4. 보드 기본 정보 조회

### `GET /v1/share-board/member/{userUuid}`

설명:

- URL의 `userUuid`로 보드 주인을 찾고 해당 공유 보드 기본 정보를 조회한다.

### Path Parameter

- `userUuid`: 공유 보드 주인의 UUID

### Response

```json
{
  "shareBoardId": 3,
  "ownerMemberId": 12,
  "ownerNickname": "alice",
  "ownerUserUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "isOwner": false
}
```

### Field 설명

- `shareBoardId`: 내부 보드 ID
- `ownerMemberId`: 보드 소유자 회원 ID
- `ownerNickname`: 보드 소유자 닉네임
- `ownerUserUuid`: 보드 URL 식별자
- `isOwner`: 현재 로그인 사용자가 보드 owner인지 여부


## 5. Bookmark 탭 조회

### `GET /v1/share-board/member/{userUuid}/postits/bookmarks`

설명:

- 해당 공유 보드의 Bookmark 탭 포스트잇 목록을 조회한다.
- `BOOKMARK`는 질문과 답변을 모두 내려줘야 한다.
- 정렬 기준은 `updatedAt DESC` 이다.
- Bookmark 포스트잇의 `updatedAt`은 `share_board_postit_layout.updatedAt` 기준으로 사용한다.
- 프론트는 응답 순서를 기준으로 섹션 배치를 결정한다.
- 섹션 정보 자체는 DB에 저장하지 않는다.

### Path Parameter

- `userUuid`: 공유 보드 주인의 UUID

### Query Parameter

- `page` optional, default `0`
- page size is fixed to `12`

### Response

```json
{
  "ownerUserUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "items": [
    {
      "postitType": "BOOKMARK",
      "sourceId": 15,
      "question": "자기소개 해주세요",
      "answer": "안녕하세요. 저는 ...",
      "answerKeyword": "성장",
      "interviewId": 8,
      "interviewUrl": "https://j14a307.p.ssafy.io/interview/share/abc123",
      "createdAt": "2026-03-25T10:00:00",
      "updatedAt": "2026-03-25T10:20:00",
      "layout": {
        "colorCode": "yellow",
        "rotationDeg": -2.5,
        "positionX": 0.12,
        "positionY": 0.30,
        "zIndex": 1
      },
      "permissions": {
        "canEditLayout": false
      }
    }
  ],
  "currentPage": 0,
  "hasNext": true
}
```

### Item Field 설명

- `postitType`: 항상 `BOOKMARK`
- `sourceId`: `interview_answer.qna_id`
- `question`: 질문 텍스트
- `answer`: 답변 텍스트
- `answerKeyword`: 답변 키워드
- `interviewId`: 인터뷰 ID
- `interviewUrl`: 공유 인터뷰 URL
- `createdAt`: 포스트잇 생성 기준 시각
- `updatedAt`: 포스트잇 최신 수정 시각
- `layout`: 레이아웃 정보
- `permissions.canEditLayout`: 현재 사용자 기준 레이아웃 수정 가능 여부


## 6. Question 탭 조회

### `GET /v1/share-board/member/{userUuid}/postits/questions`

설명:

- 해당 공유 보드의 Question 탭 포스트잇 목록을 조회한다.
- 정렬 기준은 `updatedAt DESC` 이다.
- 프론트는 응답 순서를 기준으로 섹션 배치를 결정한다.
- 섹션 정보 자체는 DB에 저장하지 않는다.

### Path Parameter

- `userUuid`: 공유 보드 주인의 UUID

### Query Parameter

- `page` optional, default `0`
- page size is fixed to `12`

### Response

```json
{
  "ownerUserUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "items": [
    {
      "postitType": "QUESTION",
      "sourceId": 32,
      "questionId": 32,
      "author": {
        "memberId": 20,
        "nickname": "guest1"
      },
      "content": "가장 힘들었던 경험은?",
      "status": "OPEN",
      "statusLabel": "WAITING",
      "startedInterviewId": null,
      "createdAt": "2026-03-25T11:00:00",
      "updatedAt": "2026-03-25T11:00:00",
      "permissions": {
        "canEditContent": false,
        "canDelete": false,
        "canEditLayout": false
      },
      "layout": {
        "colorCode": "pink",
        "rotationDeg": 1.5,
        "positionX": 0.55,
        "positionY": 0.42,
        "zIndex": 2
      }
    }
  ],
  "currentPage": 0,
  "hasNext": true
}
```

### Permission 규칙

- `canEditContent`
  - 질문 작성자 본인이고 `USED` 전 상태일 때 가능
- `canDelete`
  - 기존 질문 삭제 정책에 따름
- `canEditLayout`
  - 보드 owner면 가능
  - 질문 작성자 본인이면 자기 질문 포스트잇에 대해 가능


## 7. 질문 생성

### `POST /v1/share-board/member/{userUuid}/questions`

설명:

- 해당 공유 보드에 방문자가 질문 포스트잇을 생성한다.
- 생성 시 레이아웃 row도 함께 생성한다.
- 색상은 시스템이 자동 지정한다.

### Path Parameter

- `userUuid`: 공유 보드 주인의 UUID

### Request

```json
{
  "content": "가장 힘들었던 경험은 무엇인가요?"
}
```

### Request Field 설명

- `content`: 질문 내용

### Response

```json
{
  "postitType": "QUESTION",
  "sourceId": 41,
  "questionId": 41,
  "shareBoardId": 3,
  "author": {
    "memberId": 20,
    "nickname": "guest1"
  },
  "content": "가장 힘들었던 경험은 무엇인가요?",
  "status": "OPEN",
  "statusLabel": "WAITING",
  "startedInterviewId": null,
  "createdAt": "2026-03-25T12:00:00",
  "updatedAt": "2026-03-25T12:00:00",
  "permissions": {
    "canEditContent": true,
    "canDelete": true,
    "canEditLayout": true
  },
  "layout": {
    "colorCode": "pink",
    "rotationDeg": -1.0,
    "positionX": 0.45,
    "positionY": 0.20,
    "zIndex": 3
  }
}
```


## 8. 질문 수정

### `PATCH /v1/share-board/question/{questionId}`

설명:

- 질문 내용만 수정한다.
- 레이아웃은 이 API로 수정하지 않는다.

### Path Parameter

- `questionId`: 질문 ID

### Request

```json
{
  "content": "수정된 질문 내용입니다."
}
```

### Response

```json
{
  "questionId": 41,
  "content": "수정된 질문 내용입니다.",
  "status": "OPEN",
  "statusLabel": "WAITING",
  "updatedAt": "2026-03-25T12:10:00",
  "permissions": {
    "canEditContent": true,
    "canDelete": true,
    "canEditLayout": true
  }
}
```


## 9. 질문 삭제

### `DELETE /v1/share-board/question/{questionId}`

설명:

- 질문 포스트잇을 삭제한다.
- 관련 layout row도 함께 정리한다.

### Path Parameter

- `questionId`: 질문 ID

### Response

```json
{
  "questionId": 41,
  "deleted": true
}
```


## 10. 질문 기반 인터뷰 시작

### `POST /v1/share-board/question/{questionId}/start-interview`

설명:

- 보드 owner가 특정 질문 포스트잇을 기반으로 인터뷰를 시작한다.

### Path Parameter

- `questionId`: 질문 ID

### Response

```json
{
  "sourceQuestionId": 32,
  "questionStatus": "USED",
  "questionStatusLabel": "INTERVIEW_STARTED",
  "interview": {
    "interviewId": 101,
    "questionId": 201,
    "interviewTitle": "가장 힘들었던 경험은?",
    "ttsAudioBase64": "..."
  }
}
```


## 11. 포스트잇 레이아웃 수정

### `PATCH /v1/share-board/member/{userUuid}/postits/{postitType}/{sourceId}/layout`

설명:

- 포스트잇의 위치/기울기/z-index를 수정한다.
- 색상은 수정 불가다.

### Path Parameter

- `userUuid`: 보드 주인의 UUID
- `postitType`: `BOOKMARK` 또는 `QUESTION`
- `sourceId`: 원본 포스트잇 ID

### Request

```json
{
  "rotationDeg": -2.5,
  "positionX": 0.12,
  "positionY": 0.30,
  "zIndex": 1
}
```

### Request Field 설명

- `rotationDeg`: 포스트잇 기울기
- `positionX`: 가로 위치 비율
- `positionY`: 세로 위치 비율
- `zIndex`: 겹침 순서

### Response

```json
{
  "postitType": "QUESTION",
  "sourceId": 32,
  "layout": {
    "colorCode": "pink",
    "rotationDeg": -2.5,
    "positionX": 0.12,
    "positionY": 0.30,
    "zIndex": 1
  },
  "permissions": {
    "canEditLayout": true
  }
}
```

### 권한 규칙

- 보드 owner: 모든 포스트잇 수정 가능
- 질문 작성자: 자신의 `QUESTION` 포스트잇만 수정 가능
- 일반 방문자: 수정 불가
- `BOOKMARK`는 보드 owner만 수정 가능


## 12. 에러 케이스 예시

### 공통

- 존재하지 않는 `userUuid`
- 존재하지 않는 `questionId`
- 존재하지 않는 `sourceId`
- 권한 없는 레이아웃 수정
- 이미 `USED` 상태인 질문 수정

### 예시 응답

```json
{
  "isSuccess": false,
  "code": "403",
  "message": "해당 포스트잇 레이아웃을 수정할 수 없습니다."
}
```


## 13. 내부 구현 메모

- 회원가입 시 `share_board` 자동 생성
- 질문 생성 시 `share_board_postit_layout` 자동 생성
- 북마크 공유 처리 시 `share_board_postit_layout` 자동 생성
- 색상은 서버가 시스템 규칙으로 지정
- 조회 응답에는 `layout`과 `permissions`를 같이 넣어 프론트 분기 최소화


## 14. 최종 요약

이 명세서의 핵심은 아래와 같다.

- 보드 식별은 `userUuid`
- 화면은 `Bookmark`, `Question` 탭으로 분리
- 질문 수정과 레이아웃 수정은 API를 분리
- 레이아웃 수정은 색상을 제외한 위치/기울기/z-index만 허용
- 권한은 owner와 질문 작성자 기준으로 계산
- 탭 조회는 `page` 기준 무한 스크롤이며 정렬 기준은 `updatedAt DESC` 다


## 15. 프론트 요청 예시

이 섹션은 프론트가 실제로 어떤 값을 URL과 JSON body에 담아서 보내야 하는지 정리한 것이다.

중요:

- `userUuid`, `questionId`, `postitType`, `sourceId` 는 JSON body가 아니라 URL path에 들어간다.
- JSON body에는 실제 수정/생성 데이터만 담는다.


### 15.1 보드 기본 정보 조회

페이지 URL:

```text
/share-board/8d3c2c1a-xxxx-xxxx-xxxx
```

프론트가 읽는 값:

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx"
}
```

실제 요청:

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx"
}
```


### 15.2 Bookmark 탭 조회

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/bookmarks?page=1"
}
```

첫 페이지라면:

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/bookmarks?page=0"
}
```


### 15.3 Question 탭 조회

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/questions?page=1"
}
```

첫 페이지라면:

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/questions?page=0"
}
```


### 15.4 질문 생성

프론트가 보내는 값:

```json
{
  "content": "가장 힘들었던 경험은 무엇인가요?"
}
```

실제 요청:

```json
{
  "method": "POST",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/questions",
  "body": {
    "content": "가장 힘들었던 경험은 무엇인가요?"
  }
}
```


### 15.5 질문 수정

프론트가 알고 있는 값:

```json
{
  "questionId": 41,
  "content": "수정된 질문 내용입니다."
}
```

실제 요청:

```json
{
  "method": "PATCH",
  "url": "/v1/share-board/question/41",
  "body": {
    "content": "수정된 질문 내용입니다."
  }
}
```


### 15.6 질문 삭제

프론트가 알고 있는 값:

```json
{
  "questionId": 41
}
```

실제 요청:

```json
{
  "method": "DELETE",
  "url": "/v1/share-board/question/41"
}
```


### 15.7 질문 기반 인터뷰 시작

프론트가 알고 있는 값:

```json
{
  "questionId": 32
}
```

실제 요청:

```json
{
  "method": "POST",
  "url": "/v1/share-board/question/32/start-interview"
}
```


### 15.8 포스트잇 레이아웃 수정

예시 1. 보드 주인이 북마크 포스트잇 레이아웃 수정

프론트가 알고 있는 값:

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "postitType": "BOOKMARK",
  "sourceId": 15,
  "rotationDeg": -2.5,
  "positionX": 0.12,
  "positionY": 0.30,
  "zIndex": 1
}
```

실제 요청:

```json
{
  "method": "PATCH",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/BOOKMARK/15/layout",
  "body": {
    "rotationDeg": -2.5,
    "positionX": 0.12,
    "positionY": 0.30,
    "zIndex": 1
  }
}
```

예시 2. 질문 작성자가 자신의 질문 포스트잇 레이아웃 수정

```json
{
  "method": "PATCH",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/QUESTION/32/layout",
  "body": {
    "rotationDeg": 1.5,
    "positionX": 0.55,
    "positionY": 0.42,
    "zIndex": 2
  }
}
```

주의:

- `colorCode`는 보내지 않는다.
- 색상은 서버가 생성 시 자동 지정한다.


### 15.9 프론트 상태 예시

페이지에서 보통 프론트가 관리하는 최소 상태는 아래 정도다.

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "activeTab": "BOOKMARK",
  "boardInfo": {
    "shareBoardId": 3,
    "ownerMemberId": 12,
    "ownerNickname": "alice",
    "ownerUserUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
    "isOwner": false
  }
}
```


### 15.10 프론트 호출 순서 예시

1. 페이지 진입

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx"
}
```

2. 기본 탭이 `BOOKMARK`인 경우

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/bookmarks?page=0"
}
```

3. 사용자가 `QUESTION` 탭 클릭

```json
{
  "method": "GET",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/postits/questions?page=0"
}
```

4. 사용자가 질문 작성

```json
{
  "method": "POST",
  "url": "/v1/share-board/member/8d3c2c1a-xxxx-xxxx-xxxx/questions",
  "body": {
    "content": "가장 힘들었던 경험은 무엇인가요?"
  }
}
```

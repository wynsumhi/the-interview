# 공유 보드 API 명세서

## 공통 응답 형식

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| isSuccess | 성공 여부 | Boolean |  | X | true |
| code | 응답 코드 | Integer |  | X | 200 |
| message | 메시지 | String |  | X | 성공입니다. |
| result | 결과 데이터 | Json |  | O | `{ ... }` |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
    "sample": "data"
  }
}
```

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": null
}
```

```json
{
  "isSuccess": false,
  "code": 400,
  "message": "잘못된 요청입니다.",
  "result": {
    "reason": "error data"
  }
}
```

```json
Error Code Type
BAD_REQUEST = 400 ( 잘못된 요청입니다. )
UNAUTHORIZED = 401 ( 로그인 인증이 필요합니다. )
FORBIDDEN = 403 ( 금지된 요청입니다. )
NOT_FOUND = 404 ( 리소스를 찾을 수 없습니다. )
INTERNAL_SERVER_ERROR = 500 ( 서버 에러, 관리자에게 문의 바랍니다. )
```


## 1. 공유 보드 기본 정보 조회

### `GET /v1/share-board/member/{userUuid}`

- URL의 `userUuid`로 보드 주인을 찾고 해당 공유 보드 기본 정보를 조회한다.
- `userUuid`를 알면 누구나 접근 가능하다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| userUuid | 공유 보드 주인의 UUID | String | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| page | 현재 페이지 번호 | Integer | O | 0 |

**Example**

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "page": 0
}
```

**Query parameter**

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| page | 현재 페이지 번호 | Integer | O | 0 |

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.shareBoardId | 내부 공유 보드 ID | Integer |  | X | 3 |
| result.ownerMemberId | 보드 주인 회원 ID | Integer |  | X | 12 |
| result.ownerNickname | 보드 주인 닉네임 | String |  | X | alice |
| result.ownerUserUuid | 보드 주인 UUID | String |  | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| result.isOwner | 현재 로그인 사용자의 owner 여부 | Boolean |  | X | false |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
    "shareBoardId": 3,
    "ownerMemberId": 12,
    "ownerNickname": "alice",
    "ownerUserUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
    "isOwner": false
  }
}
```

### Status

- `200 OK`
- `404 NOT_FOUND`


## 2. Bookmark 탭 조회

### `GET /v1/share-board/member/{userUuid}/postits/bookmarks`

- 해당 공유 보드의 Bookmark 탭 포스트잇 목록을 조회한다.
- Bookmark 포스트잇은 질문과 답변을 모두 반환한다.
- 정렬 기준은 `updatedAt DESC` 이다.
- Bookmark 포스트잇의 `updatedAt`은 `share_board_postit_layout.updatedAt` 기준으로 사용한다.
- 프론트는 응답 순서를 기준으로 섹션 배치를 결정한다.
- 섹션 정보 자체는 DB에 저장하지 않는다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| userUuid | 공유 보드 주인의 UUID | String | X | `8d3c2c1a-xxxx-xxxx-xxxx` |

**Example**

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx"
}
```

**Query parameter**

- 없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.ownerUserUuid | 보드 주인 UUID | String |  | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| result.items | 북마크 포스트잇 목록 | Array |  | X |  |
| result.items[].postitType | 포스트잇 타입 | String | `BOOKMARK` | X | BOOKMARK |
| result.items[].sourceId | 원본 답변 ID | Integer |  | X | 15 |
| result.items[].question | 질문 텍스트 | String |  | X | 자기소개 해주세요 |
| result.items[].answer | 답변 텍스트 | String |  | X | 안녕하세요... |
| result.items[].answerKeyword | 답변 키워드 | String |  | O | 성장 |
| result.items[].interviewId | 인터뷰 ID | Integer |  | X | 8 |
| result.items[].interviewUrl | 인터뷰 공유 URL | String |  | O | https://... |
| result.items[].createdAt | 생성 시각 | String(DateTime) |  | X | 2026-03-25T10:00:00 |
| result.items[].updatedAt | 최신 수정 시각 | String(DateTime) |  | X | 2026-03-25T10:20:00 |
| result.items[].layout.colorCode | 시스템 지정 색상 | String |  | X | yellow |
| result.items[].layout.rotationDeg | 기울기 | Number |  | X | -2.5 |
| result.items[].layout.positionX | 가로 위치 비율 | Number |  | X | 0.12 |
| result.items[].layout.positionY | 세로 위치 비율 | Number |  | X | 0.30 |
| result.items[].layout.zIndex | 레이어 순서 | Integer |  | X | 1 |
| result.items[].permissions.canEditLayout | 레이아웃 수정 가능 여부 | Boolean |  | X | false |
| result.currentPage | 현재 페이지 번호 | Integer |  | X | 0 |
| result.hasNext | 다음 페이지 존재 여부 | Boolean |  | X | true |
**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
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
}
```

### Status

- `200 OK`
- `404 NOT_FOUND`


## 3. Question 탭 조회

### `GET /v1/share-board/member/{userUuid}/postits/questions`

- 해당 공유 보드의 Question 탭 포스트잇 목록을 조회한다.
- 정렬 기준은 `updatedAt DESC` 이다.
- 프론트는 응답 순서를 기준으로 섹션 배치를 결정한다.
- 섹션 정보 자체는 DB에 저장하지 않는다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| userUuid | 공유 보드 주인의 UUID | String | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| page | 현재 페이지 번호 | Integer | O | 0 |

**Example**

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "page": 0
}
```

**Query parameter**

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| page | 현재 페이지 번호 | Integer | O | 0 |

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.ownerUserUuid | 보드 주인 UUID | String |  | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| result.items | 질문 포스트잇 목록 | Array |  | X |  |
| result.items[].postitType | 포스트잇 타입 | String | `QUESTION` | X | QUESTION |
| result.items[].sourceId | 원본 질문 ID | Integer |  | X | 32 |
| result.items[].questionId | 질문 ID | Integer |  | X | 32 |
| result.items[].author.memberId | 작성자 회원 ID | Integer |  | X | 20 |
| result.items[].author.nickname | 작성자 닉네임 | String |  | X | guest1 |
| result.items[].content | 질문 내용 | String |  | X | 가장 힘들었던 경험은? |
| result.items[].status | 질문 상태 | String | `OPEN`,`USED`,`DELETED` | X | OPEN |
| result.items[].statusLabel | 질문 상태 라벨 | String |  | X | WAITING |
| result.items[].startedInterviewId | 시작된 인터뷰 ID | Integer |  | O | null |
| result.items[].createdAt | 생성 시각 | String(DateTime) |  | X | 2026-03-25T11:00:00 |
| result.items[].updatedAt | 수정 시각 | String(DateTime) |  | X | 2026-03-25T11:00:00 |
| result.items[].permissions.canEditContent | 질문 수정 가능 여부 | Boolean |  | X | false |
| result.items[].permissions.canDelete | 질문 삭제 가능 여부 | Boolean |  | X | false |
| result.items[].permissions.canEditLayout | 레이아웃 수정 가능 여부 | Boolean |  | X | false |
| result.items[].layout.colorCode | 시스템 지정 색상 | String |  | X | pink |
| result.items[].layout.rotationDeg | 기울기 | Number |  | X | 1.5 |
| result.items[].layout.positionX | 가로 위치 비율 | Number |  | X | 0.55 |
| result.items[].layout.positionY | 세로 위치 비율 | Number |  | X | 0.42 |
| result.items[].layout.zIndex | 레이어 순서 | Integer |  | X | 2 |
| result.currentPage | 현재 페이지 번호 | Integer |  | X | 0 |
| result.hasNext | 다음 페이지 존재 여부 | Boolean |  | X | true |
**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
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
}
```

### Status

- `200 OK`
- `404 NOT_FOUND`


## 4. 질문 생성

### `POST /v1/share-board/member/{userUuid}/questions`

- 해당 공유 보드에 질문 포스트잇을 생성한다.
- 생성 시 레이아웃 row도 함께 생성되며 색상은 시스템이 자동 지정한다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| userUuid | 공유 보드 주인의 UUID | String | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| content | 질문 내용 | String | X | 가장 힘들었던 경험은 무엇인가요? |

**Example**

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "content": "가장 힘들었던 경험은 무엇인가요?"
}
```

**Query parameter**

- 없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.postitType | 포스트잇 타입 | String | `QUESTION` | X | QUESTION |
| result.sourceId | 원본 질문 ID | Integer |  | X | 41 |
| result.questionId | 질문 ID | Integer |  | X | 41 |
| result.shareBoardId | 내부 보드 ID | Integer |  | X | 3 |
| result.author.memberId | 작성자 회원 ID | Integer |  | X | 20 |
| result.author.nickname | 작성자 닉네임 | String |  | X | guest1 |
| result.content | 질문 내용 | String |  | X | 가장 힘들었던 경험은 무엇인가요? |
| result.status | 질문 상태 | String | `OPEN` | X | OPEN |
| result.statusLabel | 질문 상태 라벨 | String |  | X | WAITING |
| result.startedInterviewId | 시작된 인터뷰 ID | Integer |  | O | null |
| result.createdAt | 생성 시각 | String(DateTime) |  | X | 2026-03-25T12:00:00 |
| result.updatedAt | 수정 시각 | String(DateTime) |  | X | 2026-03-25T12:00:00 |
| result.permissions.canEditContent | 질문 수정 가능 여부 | Boolean |  | X | true |
| result.permissions.canDelete | 질문 삭제 가능 여부 | Boolean |  | X | true |
| result.permissions.canEditLayout | 레이아웃 수정 가능 여부 | Boolean |  | X | true |
| result.layout.colorCode | 시스템 지정 색상 | String |  | X | pink |
| result.layout.rotationDeg | 기울기 | Number |  | X | -1.0 |
| result.layout.positionX | 가로 위치 비율 | Number |  | X | 0.45 |
| result.layout.positionY | 세로 위치 비율 | Number |  | X | 0.20 |
| result.layout.zIndex | 레이어 순서 | Integer |  | X | 3 |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
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
}
```

### Status

- `200 OK`
- `400 BAD_REQUEST`
- `401 UNAUTHORIZED`
- `404 NOT_FOUND`


## 5. 질문 수정

### `PATCH /v1/share-board/question/{questionId}`

- 질문 내용만 수정한다.
- 레이아웃은 이 API로 수정하지 않는다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| questionId | 질문 ID | Integer | X | 41 |
| content | 수정할 질문 내용 | String | X | 수정된 질문 내용입니다. |

**Example**

```json
{
  "questionId": 41,
  "content": "수정된 질문 내용입니다."
}
```

**Query parameter**

- 없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.questionId | 질문 ID | Integer |  | X | 41 |
| result.content | 수정된 질문 내용 | String |  | X | 수정된 질문 내용입니다. |
| result.status | 질문 상태 | String |  | X | OPEN |
| result.statusLabel | 질문 상태 라벨 | String |  | X | WAITING |
| result.updatedAt | 수정 시각 | String(DateTime) |  | X | 2026-03-25T12:10:00 |
| result.permissions.canEditContent | 질문 수정 가능 여부 | Boolean |  | X | true |
| result.permissions.canDelete | 질문 삭제 가능 여부 | Boolean |  | X | true |
| result.permissions.canEditLayout | 레이아웃 수정 가능 여부 | Boolean |  | X | true |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
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
}
```

### Status

- `200 OK`
- `400 BAD_REQUEST`
- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 NOT_FOUND`


## 6. 질문 삭제

### `DELETE /v1/share-board/question/{questionId}`

- 질문 포스트잇을 삭제한다.
- 관련 레이아웃 row도 함께 정리한다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| questionId | 질문 ID | Integer | X | 41 |

**Example**

```json
{
  "questionId": 41
}
```

**Query parameter**

- 없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.questionId | 삭제된 질문 ID | Integer |  | X | 41 |
| result.deleted | 삭제 여부 | Boolean |  | X | true |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
    "questionId": 41,
    "deleted": true
  }
}
```

### Status

- `200 OK`
- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 NOT_FOUND`


## 7. 질문 기반 인터뷰 시작

### `POST /v1/share-board/question/{questionId}/start-interview`

- 보드 owner가 특정 질문 포스트잇 기반으로 인터뷰를 시작한다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| questionId | 질문 ID | Integer | X | 32 |

**Example**

```json
{
  "questionId": 32
}
```

**Query parameter**

- 없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.sourceQuestionId | 인터뷰 시작에 사용된 질문 ID | Integer |  | X | 32 |
| result.questionStatus | 질문 상태 | String |  | X | USED |
| result.questionStatusLabel | 질문 상태 라벨 | String |  | X | INTERVIEW_STARTED |
| result.interview.interviewId | 생성된 인터뷰 ID | Integer |  | X | 101 |
| result.interview.questionId | 생성된 첫 질문 ID | Integer |  | X | 201 |
| result.interview.interviewTitle | 인터뷰 제목 | String |  | X | 가장 힘들었던 경험은? |
| result.interview.ttsAudioBase64 | TTS 오디오 base64 | String |  | X | ... |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
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
}
```

### Status

- `200 OK`
- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 NOT_FOUND`


## 8. 포스트잇 레이아웃 수정

### `PATCH /v1/share-board/member/{userUuid}/postits/{postitType}/{sourceId}/layout`

- 포스트잇의 위치/기울기/z-index를 수정한다.
- 색상은 수정할 수 없다.

### Request

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| userUuid | 보드 주인의 UUID | String | X | `8d3c2c1a-xxxx-xxxx-xxxx` |
| postitType | 포스트잇 타입 | String | X | QUESTION |
| sourceId | 원본 포스트잇 ID | Integer | X | 32 |
| rotationDeg | 기울기 | Number | X | -2.5 |
| positionX | 가로 위치 비율 | Number | X | 0.12 |
| positionY | 세로 위치 비율 | Number | X | 0.30 |
| zIndex | 레이어 순서 | Integer | X | 1 |

**Example**

```json
{
  "userUuid": "8d3c2c1a-xxxx-xxxx-xxxx",
  "postitType": "QUESTION",
  "sourceId": 32,
  "rotationDeg": -2.5,
  "positionX": 0.12,
  "positionY": 0.30,
  "zIndex": 1
}
```

**Query parameter**

- 없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| result.postitType | 포스트잇 타입 | String |  | X | QUESTION |
| result.sourceId | 원본 포스트잇 ID | Integer |  | X | 32 |
| result.layout.colorCode | 시스템 지정 색상 | String |  | X | pink |
| result.layout.rotationDeg | 기울기 | Number |  | X | -2.5 |
| result.layout.positionX | 가로 위치 비율 | Number |  | X | 0.12 |
| result.layout.positionY | 세로 위치 비율 | Number |  | X | 0.30 |
| result.layout.zIndex | 레이어 순서 | Integer |  | X | 1 |
| result.permissions.canEditLayout | 레이아웃 수정 가능 여부 | Boolean |  | X | true |

**Example**

```json
{
  "isSuccess": true,
  "code": 200,
  "message": "성공입니다.",
  "result": {
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
}
```

### Status

- `200 OK`
- `400 BAD_REQUEST`
- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 NOT_FOUND`


## 9. 권한 규칙 요약

- 보드 owner
  - 모든 포스트잇 레이아웃 수정 가능
- 질문 작성자
  - 자신의 `QUESTION` 포스트잇 레이아웃 수정 가능
  - 자신의 질문 수정/삭제 가능
- 일반 방문자
  - 조회 및 질문 생성 가능
  - 레이아웃 수정 불가
- 색상
  - 누구도 수정 불가
  - 시스템이 자동 지정

# 공유 보드 통합 설계 정리

## 1. 목적

이 문서는 지금까지 정리한 공유 보드 관련 내용을 하나로 합친 통합 문서다.

포함 범위:

- 현재 DB 구조 요약
- 공유 보드/포스트잇 데이터 모델 방향
- `share_board` 필요 여부와 생성 시점
- `userUuid` 기반 공개 접근 방식
- 탭 구조에 맞춘 API 구성
- 포스트잇 레이아웃 저장 방식
- 권한 정책


## 2. 현재 서비스의 핵심 DB 구조

현재 백엔드 기준 핵심 테이블은 아래와 같다.

- `member`
- `interview`
- `interview_question`
- `interview_answer`
- `question_candidate`
- `share_board`
- `share_board_question`

관계는 대략 이렇게 본다.

```text
member
 ├─ interview
 │   └─ interview_question
 │       └─ interview_answer
 ├─ question_candidate
 └─ share_board
     └─ share_board_question

share_board_question
 └─ started_interview_id -> interview
```

핵심 포인트는 포스트잇이 DB에서 독립된 원본 엔티티가 아니라는 점이다.

- 북마크 포스트잇 원본: `interview_answer`
- 질문 포스트잇 원본: `share_board_question`

즉 포스트잇은 UI 개념이고, 원본 데이터는 이미 다른 테이블에 있다.


## 3. `share_board` 테이블은 필요한가

현재 구조에서는 필요하다.

이유:

- 사용자당 하나의 공유 보드를 표현하는 루트 개념이 필요함
- 보드 소유자를 식별해야 함
- 보드 공개 여부를 관리해야 함
- 질문 포스트잇의 소속 부모가 필요함
- 레이아웃도 결국 어느 보드에 속한 포스트잇인지 알아야 함

즉 지금 서비스가 "인터뷰 공유"와 별개로 "사람 단위의 공유 보드"를 가지는 방향이라면 `share_board`는 두는 것이 맞다.


## 4. `share_board` 생성 시점

초기 구조:

- `GET /v1/share-board/me` 호출 시 없으면 생성

변경 방향:

- 회원가입 시 자동 생성

권장 흐름:

1. 회원가입 성공
2. `member` 생성
3. 해당 회원의 `share_board` 생성

이렇게 바꾸면:

- `/v1/share-board/me` 는 순수 조회 API가 됨
- 프론트가 `shareBoardId` 존재 여부를 걱정할 필요가 없어짐
- 조회 API에 생성 부작용이 사라짐


## 5. 공유 URL은 무엇을 기준으로 하는가

공유 URL은 `shareBoardId`가 아니라 보드 주인의 `userUuid`를 기준으로 한다.

예:

```text
/share-board/{userUuid}
```

중요:

- 보드 주인도 이 URL로 본다
- 공유받은 사용자도 이 URL로 본다
- 즉 URL은 동일하고, 차이는 현재 로그인 사용자의 권한뿐이다

이 말은 곧:

- 보드 조회 API는 전부 `userUuid` 기준으로 가는 것이 가장 자연스럽고
- 보드 owner인지 여부는 URL이 아니라 로그인 사용자와 보드 owner 비교로 판단해야 한다
- `userUuid`를 알면 누구나 해당 보드에 접근 가능하다


## 6. 현재 포스트잇 설계의 핵심 해석

### 6.1 북마크 포스트잇

현재는 별도 `board_memo` 같은 테이블 없이,

- `interview_answer.share_range = true`

이면 그 답변이 공유 보드에 노출되도록 해석하고 있다.

이 구조 자체는 괜찮다.

즉:

- "보드에 노출될 원본 데이터" 때문에 별도 테이블을 만들 필요는 없다

하지만 여기서 구분이 필요하다.

- 원본 데이터 저장
- 보드에서의 표현 상태 저장

원본 데이터는 지금 구조로 충분하다.


### 6.2 질문 포스트잇

질문 포스트잇은 이미 별도 테이블 `share_board_question`로 관리되고 있다.

즉 현재 보드에는 서로 다른 2개의 원본이 공존한다.

- `BOOKMARK` -> `interview_answer`
- `QUESTION` -> `share_board_question`


## 7. 왜 `postit_layout` 테이블이 필요한가

포스트잇의:

- 색상
- 기울기
- 위치
- z-index

같은 값은 원본 데이터가 아니라 "보드에서 어떻게 보일지"에 대한 값이다.

이걸 `interview_answer`와 `share_board_question` 양쪽에 중복 컬럼으로 넣기 시작하면:

- UI 속성이 두 테이블에 중복되고
- 인터뷰 도메인과 보드 표현 도메인이 섞이고
- 이후 확장성이 나빠진다

그래서 원본은 그대로 두고, 레이아웃만 분리하는 게 맞다.


## 8. 권장 `postit_layout` 테이블

권장 테이블명:

- `share_board_postit_layout`

권장 구조:

```sql
CREATE TABLE share_board_postit_layout (
    share_board_postit_layout_id BIGSERIAL PRIMARY KEY,
    share_board_id               INTEGER NOT NULL,
    postit_type                  VARCHAR(30) NOT NULL,
    source_id                    INTEGER NOT NULL,
    color_code                   VARCHAR(20) NOT NULL,
    rotation_deg                 NUMERIC(5,2) NOT NULL DEFAULT 0,
    position_x                   NUMERIC(7,4) NOT NULL,
    position_y                   NUMERIC(7,4) NOT NULL,
    z_index                      INTEGER NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (share_board_id, postit_type, source_id)
);
```

식별 방식:

- `postit_type = BOOKMARK`
  - `source_id -> interview_answer.qna_id`
- `postit_type = QUESTION`
  - `source_id -> share_board_question.share_board_question_id`

즉 레이아웃은 `(share_board_id, postit_type, source_id)` 조합으로 식별한다.


## 9. 색상/위치/기울기 정책

### 색상

- 시스템이 생성 시 자동 지정
- 보드 주인도 변경 불가
- 질문 작성자도 변경 불가

즉 수정 API에서 `colorCode`는 받지 않는다.

### 위치

- `position_x`, `position_y`는 px보다 비율 저장 권장
- 예: `0.12`, `0.46`

이유:

- 반응형 대응이 쉬움
- 모바일/데스크탑에 안정적

### 기울기

- `rotation_deg` 저장
- 예: `-2.5`, `1.5`


## 10. 프론트 화면 구조

프론트 계획:

- 하나의 공유 보드 URL
- 탭 2개

탭 구성:

- `Bookmark`
- `Question`

이 경우 조회 API도 탭 기준으로 분리하는 편이 좋다.

이유:

- 북마크는 질문+답변 쌍이 필요함
- 질문 탭은 작성자/상태/인터뷰 시작 여부가 필요함
- 프론트가 활성 탭 기준으로 필요한 데이터만 가져오기 쉬움
- 데이터 로딩은 무한 스크롤 방식으로 하고, 탭 조회 API는 `page` 기준으로 12개씩 반환한다
- 탭 조회 정렬 기준은 `updatedAt DESC`로 한다
- 각 섹션에 어떤 포스트잇을 배치할지는 프론트가 응답 순서 기준으로 결정한다
- DB에는 섹션 정보는 저장하지 않고, 포스트잇 자체의 레이아웃 값만 저장한다


## 11. 최종 API 설계 방향

### 보드 기본 정보 조회

```http
GET /v1/share-board/member/{userUuid}
```

내부 동작:

- `userUuid -> member -> share_board`
- 현재 로그인 사용자가 있으면 owner 여부도 같이 계산 가능

비고:

- 보드 공개 범위 설정은 두지 않는다
- `userUuid`를 아는 사용자는 누구나 접근 가능하다

### Bookmark 탭 조회

```http
GET /v1/share-board/member/{userUuid}/postits/bookmarks
```

반환 데이터:

- 질문
- 답변
- 답변 키워드
- 인터뷰 정보
- 레이아웃

주의:

- `BOOKMARK`는 질문과 답변 둘 다 내려줘야 함

### Question 탭 조회

```http
GET /v1/share-board/member/{userUuid}/postits/questions
```

반환 데이터:

- 작성자
- 내용
- 상태
- 시작된 인터뷰 정보
- 수정/삭제 가능 여부
- 레이아웃

### 공개 보드에 질문 작성

```http
POST /v1/share-board/member/{userUuid}/questions
```

의미:

- 프론트가 `shareBoardId`를 몰라도 질문 생성 가능
- URL 구조와 API 식별자가 일치함


### 질문 도메인 API

### 질문 수정

```http
PATCH /v1/share-board/question/{questionId}
```

### 질문 삭제

```http
DELETE /v1/share-board/question/{questionId}
```

### 질문 기반 인터뷰 시작

```http
POST /v1/share-board/question/{questionId}/start-interview
```


### 레이아웃 수정 API

```http
PATCH /v1/share-board/member/{userUuid}/postits/{postitType}/{sourceId}/layout
```

요청 예시:

```json
{
  "rotationDeg": -2.5,
  "positionX": 0.12,
  "positionY": 0.30,
  "zIndex": 1
}
```

수정 가능 필드:

- `rotationDeg`
- `positionX`
- `positionY`
- `zIndex`

수정 불가:

- `colorCode`


## 12. 권한 정책

### 보드 owner

- 자신의 보드 조회 가능
- 보드 조회 가능
- 모든 포스트잇의 위치/기울기/z-index 수정 가능
- 질문 포스트잇 기반 인터뷰 시작 가능
- 질문 삭제 가능
- 색상 변경 불가

### 질문 작성자

- 보드 조회 가능
- 질문 작성 가능
- 자신의 질문 내용 수정 가능
- 자신의 질문 삭제 가능
- 자신의 `QUESTION` 포스트잇 위치/기울기/z-index 수정 가능
- 자신의 `QUESTION` 포스트잇 색상 변경 불가
- `BOOKMARK` 포스트잇 수정 불가

### 일반 방문자

- 보드 조회 가능
- 질문 작성 가능
- 레이아웃 수정 불가


## 13. API 경로별 식별자 기준

### `userUuid` 기준으로 하는 API

이유:

- 실제 페이지 URL이 `userUuid` 기반
- 보드 owner와 방문자가 동일 URL을 사용
- 프론트가 별도의 `shareBoardId`를 알 필요가 없음

대상:

- `GET /v1/share-board/member/{userUuid}`
- `GET /v1/share-board/member/{userUuid}/postits/bookmarks`
- `GET /v1/share-board/member/{userUuid}/postits/questions`
- `POST /v1/share-board/member/{userUuid}/questions`
- `PATCH /v1/share-board/member/{userUuid}/postits/{postitType}/{sourceId}/layout`

### `questionId` 기준으로 유지하는 API

이유:

- 질문 수정/삭제/인터뷰 시작은 이미 질문 단건 식별자로 충분함

대상:

- `PATCH /v1/share-board/question/{questionId}`
- `DELETE /v1/share-board/question/{questionId}`
- `POST /v1/share-board/question/{questionId}/start-interview`


## 14. 내부 구현에서 추가될 점

- 회원가입 시 `share_board` 자동 생성
- 보드 조회 및 수정 시 `userUuid -> member -> share_board` 조회 로직 필요
- 질문 생성 시 기본 `layout` row 생성
- 북마크가 공유 처리될 때 기본 `layout` row 생성
- `layout.color_code`는 시스템이 자동 지정
- 질문 삭제 시 관련 `layout` row 정리


## 15. 최종 정리

한 줄로 정리하면 아래와 같다.

- 공유 보드 원본 데이터는 기존 테이블 유지
- 보드 자체는 `share_board`로 유지
- 공유 URL은 `userUuid` 기반
- 보드 owner와 방문자 모두 동일 URL 사용
- `userUuid`를 알면 누구나 보드 접근 가능
- 조회/질문 작성/레이아웃 수정 API는 `userUuid` 기준으로 통일
- 포스트잇 색상은 시스템 지정
- 위치/기울기/z-index만 사용자 수정 가능
- 질문 작성자는 자기 질문 포스트잇만 수정 가능
- 보드 owner는 모든 포스트잇 레이아웃 수정 가능

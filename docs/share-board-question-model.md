# Share Board Question Data Model Draft

## 1. 엔티티 초안

### 1.1 ShareBoardQuestion

필드 제안:

- `id`
- `shareBoardId`
- `authorMemberId`
- `boardOwnerMemberId`
- `content`
- `status`
- `startedInterviewId`
- `createdAt`
- `updatedAt`
- `deletedAt` 또는 `isDeleted`

### 1.2 Share Board Composition

공유 보드는 개념적으로 아래 2개 보드로 구성된다.

- `Bookmark Board`: 보드 주인이 북마크한 Q&A 포스트잇 보드
- `Question Board`: 방문자가 질문을 남기는 포스트잇 보드

## 2. 필드 설명

### 2.1 `shareBoardId`

질문이 어느 공유 보드에 속하는지 식별한다.

### 2.2 `authorMemberId`

질문 작성자다. 수정/삭제 권한 검증에 사용된다.

### 2.3 `boardOwnerMemberId`

보드 주인을 직접 들고 가면 조회 최적화에 유리할 수 있다.

단, `shareBoard`에서 역으로 유추 가능하면 중복 저장 없이 조회 시 조인으로 해결할 수도 있다.

### 2.4 `content`

사용자가 입력한 질문 본문이다.

### 2.5 `status`

질문의 현재 생명주기 상태다.

추천값:

- `OPEN`
- `SELECTED`
- `USED`
- `DELETED`

표시용 라벨 예시:

- `OPEN` -> `대기 중`
- `USED` -> `인터뷰 진행됨`

### 2.6 `startedInterviewId`

이 질문이 실제로 어떤 인터뷰 시작에 사용되었는지 연결하는 값이다.

## 3. 관계 초안

- 하나의 `ShareBoard`는 여러 개의 `ShareBoardQuestion`을 가진다.
- 하나의 `Member`는 여러 개의 `ShareBoardQuestion`을 작성할 수 있다.
- 하나의 `ShareBoardQuestion`은 0개 또는 1개의 `Interview` 시작과 연결될 수 있다.

## 4. 인덱스 제안

- `(share_board_id, created_at desc)`
- `(author_member_id, created_at desc)`
- `(board_owner_member_id, created_at desc)` 또는 보드 기반 조회로 대체

## 5. 삭제 전략

권장:

- 소프트 삭제

이유:

- 인터뷰 시작에 사용된 질문 추적 가능
- 운영상 분쟁 대응 가능
- "삭제된 질문 제외 조회" 조건만 추가하면 됨

## 6. DTO 초안

### 6.1 생성 요청

```json
{
  "content": "질문 내용"
}
```

### 6.2 수정 요청

```json
{
  "content": "수정된 질문 내용"
}
```

### 6.3 조회 응답

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
  "startedInterviewId": null,
  "createdAt": "2026-03-24T13:20:00",
  "updatedAt": "2026-03-24T13:20:00",
  "isMine": false
}
```

## 7. 확장 포인트

추후 필요 시 아래 필드를 추가할 수 있다.

- `memoId`: 특정 포스트잇에 연결할 경우
- `isAnonymous`: 익명 질문 허용 시
- `selectedAt`
- `usedAt`
- `deletedByMemberId`

## 8. Open Questions

1. `boardOwnerMemberId`를 중복 저장할지
2. 질문 상태를 MVP부터 세분화할지

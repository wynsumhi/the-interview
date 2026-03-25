/** GET /v1/share-board/member/{userUuid} 응답의 result 필드 */
export interface ShareBoardInfo {
  isOwner: boolean;
  ownerMemberId: number;
  ownerNickname: string;
  ownerUserUuid: string;
  shareBoardId: number;
}

/** 포스트잇 카드의 위치/스타일 정보 */
export interface BookmarkPostitLayout {
  colorCode: string;
  rotationDeg: number;
  /** 0~1 범위, 캔버스 너비 대비 가로 위치 */
  positionX: number;
  /** 0~1 범위, 캔버스 높이 대비 세로 위치 */
  positionY: number;
  zIndex: number;
}

/** 포스트잇 카드에 대해 현재 사용자가 가진 권한 정보 */
export interface BookmarkPostitPermissions {
  /** 레이아웃(위치/스타일) 편집 가능 여부 */
  canEditLayout: boolean;
}

/** GET /v1/share-board/member/{userUuid}/postits/bookmarks 응답 items 배열 한 항목 */
export interface BookmarkPostitItem {
  postitType: string;
  /** 포스트잇의 고유 식별자 (sourceId) */
  sourceId: number;
  question: string;
  answer: string;
  /** 답변 핵심 키워드 (카드 기본 화면에 표시) */
  answerKeyword: string;
  interviewId: number;
  interviewUrl: string;
  createdAt: string;
  updatedAt: string;
  layout: BookmarkPostitLayout;
  permissions: BookmarkPostitPermissions;
}

/** GET /v1/share-board/member/{userUuid}/postits/bookmarks 응답의 result 필드 */
export interface BookmarkPostitPageResult {
  ownerUserUuid: string;
  items: BookmarkPostitItem[];
  currentPage: number;
  /** 다음 페이지 존재 여부 (true면 page + 1로 추가 요청 가능) */
  hasNext: boolean;
}

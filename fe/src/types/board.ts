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

/** GET /v1/share-board/member/{userUuid}/postits/questions 응답 items 배열 한 항목의 작성자 정보 */
export interface QuestionAuthor {
  memberId: number;
  nickname: string;
}

/** GET /v1/share-board/member/{userUuid}/postits/questions 응답 items 배열 한 항목의 권한 정보 */
export interface QuestionPostitPermissions {
  canEditContent: boolean;
  canDelete: boolean;
  canEditLayout: boolean;
}

/** GET /v1/share-board/member/{userUuid}/postits/questions 응답 items 배열 한 항목 */
export interface QuestionPostitItem {
  postitType: string;
  /** 포스트잇의 고유 식별자 */
  sourceId: number;
  questionId: number;
  author: QuestionAuthor;
  /** 질문 내용 (카드 기본 화면에 표시) */
  content: string;
  status: string;
  /** 사람이 읽기 좋은 상태 레이블 (예: "WAITING") */
  statusLabel: string;
  /** 연결된 면접 ID, 아직 시작하지 않은 경우 null */
  startedInterviewId: number | null;
  createdAt: string;
  updatedAt: string;
  permissions: QuestionPostitPermissions;
  layout: BookmarkPostitLayout;
}

/** GET /v1/share-board/member/{userUuid}/postits/questions 응답의 result 필드 */
export interface QuestionPostitPageResult {
  ownerUserUuid: string;
  items: QuestionPostitItem[];
  currentPage: number;
  /** 다음 페이지 존재 여부 (true면 page + 1로 추가 요청 가능) */
  hasNext: boolean;
}

/** POST /v1/share-board/question/{questionId}/start-interview 응답의 interview 필드 */
export interface StartQuestionInterviewData {
  interviewId: number;
  questionId: number;
  interviewTitle: string;
  ttsAudioBase64: string;
}

/** POST /v1/share-board/question/{questionId}/start-interview 응답의 result 필드 */
export interface StartQuestionInterviewResult {
  sourceQuestionId: number;
  questionStatus: string;
  questionStatusLabel: string;
  interview: StartQuestionInterviewData;
}

/** PATCH /v1/share-board/question/{questionId} 응답의 result 필드 */
export interface UpdateQuestionResult {
  questionId: number;
  shareBoardId: number;
  author: QuestionAuthor;
  content: string;
  status: string;
  statusLabel: string;
  startedInterviewId: number | null;
  createdAt: string;
  updatedAt: string;
  isMine: boolean;
  canEdit: boolean;
  canDelete: boolean;
}

/** POST /v1/share-board/member/{userUuid}/questions 요청 바디 */
export interface CreateQuestionRequest {
  /** 작성할 질문 내용 */
  content: string;
}

/** POST /v1/share-board/member/{userUuid}/questions 응답의 result 필드 */
export interface CreateQuestionResult {
  postitType: string;
  sourceId: number;
  questionId: number;
  shareBoardId: number;
  author: QuestionAuthor;
  content: string;
  status: string;
  statusLabel: string;
  startedInterviewId: number | null;
  createdAt: string;
  updatedAt: string;
  permissions: QuestionPostitPermissions;
  layout: BookmarkPostitLayout;
}

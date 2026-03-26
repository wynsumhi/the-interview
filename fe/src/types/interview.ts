// ─────────────────────────────────────────────
// 인터뷰 상세 API (GET /api/v1/interview/{interviewId})
// ─────────────────────────────────────────────

/** 상세 페이지의 단일 Q&A 항목 (result.interview 배열 원소) */
export interface InterviewDetailEntry {
  interviewAnswerId: number; // 북마크 API 요청에 사용하는 답변 고유 ID
  interviewQuestion: string; // AI 질문 원문
  interviewText: string; // STT 처리된 사용자 답변 텍스트
  interviewVoice: string; // 사용자 답변 음성 URL (.webm)
  interviewShareRange?: boolean; // 북마크 여부: true = 북마크됨(공개), false = 북마크 해제
}

/** GET /api/v1/interview/{interviewId} 응답의 result 필드 */
export interface InterviewDetailItem {
  interviewId: number;
  interviewTitle: string;
  interviewDate: string;
  interview: InterviewDetailEntry[]; // Q&A 목록
}

// ─────────────────────────────────────────────
// Explore 목록 API (GET /api/v1/interview/explore?page=N)
// ─────────────────────────────────────────────

/** Explore 목록의 단일 인터뷰 항목 */
export interface ExploreInterviewItem {
  interviewId: number;
  interviewTitle: string;
  interviewDate: string; // "2026-03-15" 형식
  fusedEmotion: EmotionType; // 대표 감정 (커버 색상 및 아이콘에 사용)
}

/** GET /api/v1/interview/explore 응답의 result 필드 */
export interface ExplorePage {
  currentPage: number;
  interviews: ExploreInterviewItem[];
  totalPages: number;
}

// ─────────────────────────────────────────────
// 인터뷰 히스토리 목록 API (GET /api/v1/interview?page=N)
// ─────────────────────────────────────────────

/** 5개 감정 점수 (각 0.00 ~ 1.00) */
export interface SentimentScores {
  calm: number; // 평온 — #B7D4BB
  tense: number; // 긴장 — #F3C5C5
  anxious: number; // 불안 — #FFF3A6
  low: number; // 우울 — #BCCEF6
  engaged: number; // 집중 — #FBCDB6
}

/** 목록의 단일 인터뷰 항목 */
export interface InterviewHistoryItem {
  interviewDate: string; // "2026-03-15" 형식의 날짜 문자열
  interviewId: number; // 인터뷰 고유 ID (상세 페이지 링크에 사용)
  interviewTitle: string; // AI가 생성한 인터뷰 제목
  sentimentScores?: SentimentScores; // 감정 점수 (없으면 시각화 미표시)
}

/** GET /api/v1/interview 응답의 result 필드 */
export interface InterviewHistoryPage {
  currentPage: number; // 현재 페이지 번호 (1-based)
  interviews: InterviewHistoryItem[]; // 현재 페이지의 인터뷰 목록
  totalPages: number; // 전체 페이지 수 (무한스크롤 종료 조건)
}

/** 공통 API 래퍼 응답 구조 */
export interface ApiResponse<T> {
  isSuccess: boolean;
  code: string;
  message: string;
  result: T;
}

// 주요 감정 키워드
export type EmotionType = "joy" | "serenity" | "anger" | "sadness" | "insight";

// 공개 범위 설정
export type ShareRange = "PUBLIC" | "PRIVATE" | "FRIENDS";

// 인터뷰 진행 상태
export type InterviewStatus =
  | "CONNECTING" // WebSocket 연결 중
  | "AI_THINKING" // AI 질문 생성 중
  | "AI_SPEAKING" // AI 질문 음성 재생
  | "USER_RECORDING" // 사용자 답변 녹음
  | "UPLOADING" // 사용자 음성 업로드
  | "WAITING_AI" // AI가 다음 질문 생성
  | "FINISHED"; // 인터뷰 종료

// 사용자 관련
export interface Member {
  member_id: number;
  email: string;
  nickname: string;
  created_at: string;
  updated_at: string;
  is_first_finished: boolean; // 첫 온보딩 인터뷰 완료 여부
}

// 인터뷰의 핵심: 질문-답변-감정 (QnA 세트)
// 감정 스탬프 UI를 위해 QnA 하나에 답변과 감정이 묶여있는 구조
export interface InterviewQna {
  question_id: number;
  sequence: number; // 질문 순서
  question_text: string;

  // 답변 데이터 (답변이 아직 없는 진행 중 상태일 수 있으므로 선택적?)
  answer?: {
    answer_text: string;
    answer_audio_url: string;
    created_at: string;
  };

  // 감정 분석 결과 (Q&A 한 짝마다 도출되는 감정 리스트)
  // '화남' 스탬프 등을 찍기 위해 리스트 형태나 대표 감정을 가집니다.
  emotions: {
    emotion_label: string; // 감정 상태
    emotion_type: EmotionType; // 아이콘/컬러 매칭을 위한 영문 키
    emotion_rate: number; // 감정 강도 (스탬프 크기나 사탕 높이 조절용)
  }[];
}

// 인터뷰 전체 및 리포트 (Archive & Magazine)
// 아카이브 리스트에서 사용하는 기본 정보
export interface Interview {
  interview_id: number;
  member_id: number;
  title: string; // AI 요약 제목
  status: InterviewStatus;
  share_range: ShareRange;
  created_at: string;
  updated_at: string;
}

// 상세 페이지에서 사용할 때는 QnA 리스트와 리포트가 포함
export interface InterviewDetail extends Interview {
  qna_list: InterviewQna[]; // 이 안에 질문, 답변, 감정이 다 들어있음
  report?: {
    report_id: number;
    summary: string; // 전체 인터뷰 요약문
    created_at: string;
  };
}

// 온보딩(최초 인터뷰) 관련
export interface OnboardingQuestion {
  onboarding_id: number;
  question_text: string;
  question_audio_url: string;
}

export interface OnboardingAnswer {
  onboarding_answer_id: number;
  member_id: number;
  onboarding_id: number;
  answer_text: string;
  answer_audio_url: string;
}

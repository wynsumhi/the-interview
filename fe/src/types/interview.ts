// 추후 수정 예정

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

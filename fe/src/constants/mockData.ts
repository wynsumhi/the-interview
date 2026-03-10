import { InterviewDetail, Member } from "@/types/interview";

// 테스트용 사용자 정보
export const MOCK_USER: Member = {
  member_id: 1,
  email: "kurby@ssafy.com",
  nickname: "슬픈 커비",
  created_at: "2026-01-01T00:00:00Z",
  updated_at: "2026-02-10T00:00:00Z",
  is_first_finished: true,
};

// 테스트용 인터뷰 상세 데이터 (매거진용) - 'Q&A마다 쌓이는 감정'을 시각화하기 위한 구조
export const MOCK_INTERVIEW_DETAIL: InterviewDetail = {
  interview_id: 101,
  member_id: 1,
  title: "비 오는 날의 조각들",
  status: "COMPLETED",
  share_range: "PRIVATE",
  created_at: "2026-03-10T14:00:00Z",
  updated_at: "2026-03-10T14:30:00Z",
  qna_list: [
    {
      question_id: 1,
      sequence: 1,
      question_text: "빗소리를 들으면 어떤 기분이 드나요?",
      answer: {
        answer_text: "마음이 차분해지고 생각이 정리되는 기분이에요.",
        answer_audio_url: "",
        created_at: "2026-03-10T14:05:00Z",
      },
      emotions: [
        { emotion_label: "평온", emotion_type: "serenity", emotion_rate: 90 },
      ],
    },
  ],
};

// 아카이브 리스트용 데이터 모음
export const MOCK_ARCHIVE_LIST: InterviewDetail[] = [
  MOCK_INTERVIEW_DETAIL,
  {
    ...MOCK_INTERVIEW_DETAIL,
    interview_id: 102,
    title: "새로운 프로젝트를 시작하는 설렘",
    created_at: "2026-03-05T10:00:00Z",
    qna_list: [
      {
        question_id: 3,
        sequence: 1,
        question_text: "새로운 시작을 앞두고 가장 기대되는 점은?",
        answer: {
          answer_text:
            "제가 만든 서비스가 세상에 나올 생각을 하니 너무 기뻐요!",
          answer_audio_url: "/audio/sample3.wav",
          created_at: "2026-03-05T10:05:00Z",
        },
        emotions: [
          { emotion_label: "기쁨", emotion_type: "joy", emotion_rate: 95 },
        ],
      },
    ],
  },
];

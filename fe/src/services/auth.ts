// src/services/auth.ts
// 인증 관련 API 호출 함수를 모아두는 파일
// UI(페이지)와 API 호출 로직을 분리해서 코드를 깔끔하게 유지하기 위함

import instance from "@/lib/axios"; // 공통 axios 인스턴스 (baseURL, 토큰 설정 포함)

// 회원가입 요청 시 서버에 전달할 데이터 형태 정의
// interface = 객체의 "모양"을 TypeScript에게 알려주는 타입 선언
export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

// 회원가입 성공 시 서버가 돌려주는 데이터 형태
export interface SignupResponse {
  memberId: number; // 서버에서 생성된 사용자 고유 ID
}

// 회원가입 API 호출 함수
// async/await: 서버 응답을 기다렸다가 결과를 반환하는 비동기 함수
// POST /api/v1/auth/signup 엔드포인트로 요청
export const signup = async (data: SignupRequest): Promise<SignupResponse> => {
  const response = await instance.post<SignupResponse>("/api/v1/auth/signup", data);
  // response.data: axios가 HTTP 응답에서 실제 JSON 바디만 꺼내줌
  return response.data;
};

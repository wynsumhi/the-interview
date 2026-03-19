// src/services/member.ts
// 회원 정보 관련 API 호출 함수를 모아두는 파일

import instance from "@/lib/axios";

// 회원 정보 조회 시 서버에 전달할 데이터 형태 정의
export interface MemberResponse {
  memberId: number;
  email: string;
  nickname: string;
}

// 회원 정보 수정 요청 시 서버에 전달할 데이터 형태 정의
export interface UpdateMemberRequest {
  nickname: string;
}

// 회원 정보 조회 API 호출 함수
// GET /auth/me 엔드포인트로 요청
export const getMe = async (): Promise<MemberResponse> => {
  const response = await instance.get("/auth/me");
  return response.data.result;
};

// 회원 정보 수정 API 호출 함수
// PATCH /auth/me 엔드포인트로 요청
export const updateMe = async (
  data: UpdateMemberRequest,
): Promise<MemberResponse> => {
  const response = await instance.patch("/auth/me", data);
  return response.data.result;
};

// 회원 탈퇴 API 호출 함수
// DELETE /auth/me 엔드포인트로 요청
export const deleteMe = async (): Promise<void> => {
  await instance.delete("/auth/me");
};

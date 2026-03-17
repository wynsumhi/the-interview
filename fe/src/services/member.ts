// src/services/member.ts
// 회원 정보 관련 API 호출 함수를 모아두는 파일

import instance from "@/lib/axios";

// 회원 정보 조회 시 서버에 전달할 데이터 형태 정의
export interface MemberResponse {
  memberId: number;
  email: string;
  nickname: string;
}

// 회원 정보 (닉네임) 수정 요청 시 서버에 전달할 데이터 형태 정의
export interface UpdateMemberRequest {
  nickname: string;
}

// 회원 정보 조회 (GET)
export const getMe = async (): Promise<MemberResponse> => {
  const response = await instance.get<MemberResponse>("/member/me");
  return response.data;
};

// 회원 정보 (닉네임) 수정 (PATCH)
export const updateMe = async (
  data: UpdateMemberRequest,
): Promise<MemberResponse> => {
  const response = await instance.patch<MemberResponse>("/member/me", data);
  return response.data;
};

// TODO: 회원 탈퇴 (DELETE)

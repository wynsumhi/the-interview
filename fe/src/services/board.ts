import instance from "@/lib/axios";
import type { ApiResponse } from "@/types/interview";
import type { ShareBoardInfo } from "@/types/board";

// GET /v1/share-board/member/{userUuid} - 공유 보드 기본 정보 조회
// isOwner: 내 보드인지 여부, ownerNickname: 보드 주인 닉네임 등
export async function fetchShareBoardInfo(userUuid: string): Promise<ShareBoardInfo> {
  const res = await instance.get<ApiResponse<ShareBoardInfo>>(
    `/share-board/member/${userUuid}`,
  );
  return res.data.result;
}

import instance from "@/lib/axios";
import type { ApiResponse } from "@/types/interview";
import type {
  ShareBoardInfo,
  BookmarkPostitPageResult,
  QuestionPostitPageResult,
} from "@/types/board";

// GET /v1/share-board/member/{userUuid} - 공유 보드 기본 정보 조회
// isOwner: 내 보드인지 여부, ownerNickname: 보드 주인 닉네임 등
export async function fetchShareBoardInfo(userUuid: string): Promise<ShareBoardInfo> {
  const res = await instance.get<ApiResponse<ShareBoardInfo>>(
    `/share-board/member/${userUuid}`,
  );
  return res.data.result;
}

// GET /v1/share-board/member/{userUuid}/postits/bookmarks?page={page}
// 북마크 탭에 표시할 포스트잇 목록을 페이지 단위로 가져옴
// hasNext가 true면 page + 1로 다음 페이지 요청 가능
export async function fetchBookmarkPostits(
  userUuid: string,
  page: number,
): Promise<BookmarkPostitPageResult> {
  const res = await instance.get<ApiResponse<BookmarkPostitPageResult>>(
    `/share-board/member/${userUuid}/postits/bookmarks`,
    { params: { page } },
  );
  return res.data.result;
}

// GET /v1/share-board/member/{userUuid}/postits/questions?page={page}
// 질문 탭에 표시할 포스트잇 목록을 페이지 단위로 가져옴
// hasNext가 true면 page + 1로 다음 페이지 요청 가능
export async function fetchQuestionPostits(
  userUuid: string,
  page: number,
): Promise<QuestionPostitPageResult> {
  const res = await instance.get<ApiResponse<QuestionPostitPageResult>>(
    `/share-board/member/${userUuid}/postits/questions`,
    { params: { page } },
  );
  return res.data.result;
}

import { useInfiniteQuery } from "@tanstack/react-query";
import { fetchQuestionPostits } from "@/services/board";

// 질문 포스트잇 목록을 무한 스크롤 방식으로 가져오는 훅
// hasNext가 true인 동안 fetchNextPage()를 호출해 다음 페이지를 추가로 불러올 수 있음
export function useQuestionPostits(userUuid: string) {
  return useInfiniteQuery({
    queryKey: ["questionPostits", userUuid],
    queryFn: ({ pageParam }) => fetchQuestionPostits(userUuid, pageParam),
    initialPageParam: 0,
    // 마지막 페이지의 hasNext가 true면 currentPage + 1을 다음 pageParam으로 반환
    // false면 undefined를 반환해 더 이상 요청하지 않음
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.currentPage + 1 : undefined,
    enabled: !!userUuid,
  });
}

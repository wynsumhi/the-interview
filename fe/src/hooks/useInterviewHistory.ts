import { useInfiniteQuery } from "@tanstack/react-query";
import { fetchInterviewHistory } from "@/services/interview";

export function useInterviewHistory() {
  return useInfiniteQuery({
    queryKey: ["interviewHistory"],

    // pageParam: 다음에 요청할 페이지 번호 (0-based)
    queryFn: async ({ pageParam }) => {
      console.log("[useInterviewHistory] API 호출 → page =", pageParam);
      const res = await fetchInterviewHistory(pageParam);
      console.log("[useInterviewHistory] 서버 응답 raw →", JSON.stringify(res));
      return res;
    },

    // 첫 요청은 0페이지부터 시작
    initialPageParam: 0,

    // allPages.length = 이미 로드된 페이지 수 → 0-based 다음 페이지 번호
    // totalPages가 서버 응답에 없으므로 마지막 페이지의 interviews가 비어있으면 종료
    getNextPageParam: (lastPage, allPages) => {
      const nextPage = allPages.length;
      const hasMore = lastPage.interviews.length > 0;
      console.log(
        "[useInterviewHistory] getNextPageParam → allPages.length =",
        allPages.length,
        "/ 마지막 페이지 interviews 수 =",
        lastPage.interviews.length,
        "/ nextPage =",
        hasMore ? nextPage : "없음(마지막 페이지)",
      );
      return hasMore ? nextPage : undefined;
    },
  });
}

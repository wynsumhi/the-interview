import { useInfiniteQuery } from "@tanstack/react-query";
import { fetchInterviewHistory } from "@/services/interview";

export function useInterviewHistory() {
  return useInfiniteQuery({
    queryKey: ["interviewHistory"],

    // pageParam: 다음에 요청할 페이지 번호 (0-based)
    queryFn: ({ pageParam }) => fetchInterviewHistory(pageParam),

    // 첫 요청은 0페이지부터 시작
    initialPageParam: 0,

    // 다음 페이지 번호를 계산하는 함수
    // lastPage.currentPage는 1-based이므로, 0-based API 파라미터로 변환
    getNextPageParam: (lastPage) => {
      const nextPage = lastPage.currentPage; // currentPage=1 → 다음 요청은 page=1
      // 마지막 페이지면 undefined 반환 → hasNextPage가 false가 됨
      return nextPage < lastPage.totalPages ? nextPage : undefined;
    },
  });
}

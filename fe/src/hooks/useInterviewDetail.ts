import { useQuery } from "@tanstack/react-query";
import { fetchInterviewDetail } from "@/services/interview";

export function useInterviewDetail(interviewId: number) {
  return useQuery({
    queryKey: ["interviewDetail", interviewId],
    queryFn: () => fetchInterviewDetail(interviewId),
    enabled: !!interviewId,
    retry: false, // 403/404는 재시도 불필요
  });
}

import { useQuery } from "@tanstack/react-query";
import { fetchPublicInterviewDetail } from "@/services/interview";

export function usePublicInterviewDetail(shareUuid: string) {
  return useQuery({
    queryKey: ["publicInterviewDetail", shareUuid],
    queryFn: () => fetchPublicInterviewDetail(shareUuid),
    enabled: !!shareUuid,
    retry: false,
  });
}

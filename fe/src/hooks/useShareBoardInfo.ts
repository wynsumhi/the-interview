import { useQuery } from "@tanstack/react-query";
import { fetchShareBoardInfo } from "@/services/board";

export function useShareBoardInfo(userUuid: string) {
  return useQuery({
    queryKey: ["shareBoardInfo", userUuid],
    queryFn: () => fetchShareBoardInfo(userUuid),
    enabled: !!userUuid,
    retry: false,
  });
}

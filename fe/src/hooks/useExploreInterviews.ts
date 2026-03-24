import { useInfiniteQuery } from "@tanstack/react-query";
import { fetchExploreInterviews } from "@/services/interview";

export function useExploreInterviews() {
  return useInfiniteQuery({
    queryKey: ["exploreInterviews"],

    queryFn: ({ pageParam }) => fetchExploreInterviews(pageParam),

    initialPageParam: 0,

    getNextPageParam: (lastPage, allPages) => {
      const nextPage = allPages.length;
      return lastPage.interviews.length > 0 ? nextPage : undefined;
    },
  });
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { InfiniteData } from "@tanstack/react-query";
import { deleteQuestion } from "@/services/board";
import type { QuestionPostitPageResult } from "@/types/board";

// 질문 포스트잇 삭제 mutation 훅
// 성공 시 캐시에서 해당 항목을 즉시 제거하여 UI에서 바로 사라지게 함
export function useDeleteQuestion(userUuid: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (questionId: number) => deleteQuestion(questionId),
    onSuccess: (data, questionId) => {
      if (!data.deleted) return;

      // 캐시에서 삭제된 questionId 항목을 필터링하여 즉시 제거
      queryClient.setQueryData<InfiniteData<QuestionPostitPageResult>>(
        ["questionPostits", userUuid],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            pages: old.pages.map((page) => ({
              ...page,
              items: page.items.filter((item) => item.questionId !== questionId),
            })),
          };
        },
      );
    },
  });
}

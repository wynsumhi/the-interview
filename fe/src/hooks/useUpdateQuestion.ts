import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { InfiniteData } from "@tanstack/react-query";
import { updateQuestion } from "@/services/board";
import type { QuestionPostitPageResult } from "@/types/board";

// 질문 포스트잇 내용 수정 mutation 훅
// 성공 시 캐시의 해당 항목 content와 updatedAt을 즉시 갱신
export function useUpdateQuestion(userUuid: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ questionId, content }: { questionId: number; content: string }) =>
      updateQuestion(questionId, content),
    onSuccess: (data) => {
      // 캐시에서 해당 질문 항목의 content와 updatedAt을 직접 갱신하여 UI 즉시 반영
      queryClient.setQueryData<InfiniteData<QuestionPostitPageResult>>(
        ["questionPostits", userUuid],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            pages: old.pages.map((page) => ({
              ...page,
              items: page.items.map((item) =>
                item.questionId === data.questionId
                  ? { ...item, content: data.content, updatedAt: data.updatedAt }
                  : item,
              ),
            })),
          };
        },
      );
    },
  });
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createQuestion } from "@/services/board";

// 질문 포스트잇 생성 mutation 훅
// 성공 시 해당 userUuid의 questionPostits 캐시를 무효화해 목록을 자동 갱신
export function useCreateQuestion(userUuid: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (content: string) => createQuestion(userUuid, content),
    onSuccess: () => {
      // 생성 성공 후 질문 목록 캐시를 무효화하여 최신 데이터를 다시 불러옴
      queryClient.invalidateQueries({ queryKey: ["questionPostits", userUuid] });
    },
  });
}

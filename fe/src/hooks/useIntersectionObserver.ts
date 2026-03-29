import { useEffect, useRef } from "react";

interface Options {
  // 콜백 실행 조건: target이 뷰포트에 보이는 비율 (0.0 ~ 1.0)
  threshold?: number;
  // 추가 실행 조건: 외부에서 "아직 다음 페이지가 있는가"를 주입받음
  enabled?: boolean;
}

/**
 * target 요소가 뷰포트에 진입할 때 onIntersect를 실행하는 훅
 * 반환한 ref를 sentinel(감시 대상) DOM 요소에 붙여 사용
 */
export function useIntersectionObserver(
  onIntersect: () => void,
  { threshold = 0.1, enabled = true }: Options = {},
) {
  const targetRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // enabled=false(다음 페이지 없음)이면 Observer 등록 자체를 건너뜀
    if (!enabled) return;

    const target = targetRef.current;
    if (!target) return;

    const observer = new IntersectionObserver(
      (entries) => {
        // sentinel이 화면에 threshold 비율 이상 보이면 콜백 실행
        if (entries[0].isIntersecting) {
          onIntersect();
        }
      },
      { threshold },
    );

    observer.observe(target);

    // 클린업: 컴포넌트 언마운트 또는 의존성 변경 시 Observer 해제
    return () => observer.disconnect();
  }, [onIntersect, threshold, enabled]);

  return targetRef;
}

"use client";

// useState: 어떤 탭이 선택됐는지 기억할 때 사용
import { use, useEffect } from "react";
import { useShareBoardInfo } from "@/hooks/useShareBoardInfo";
import { useBookmarkPostits } from "@/hooks/useBookmarkPostits";
import { useBoardStore } from "@/store/useBoardStore";
import MemoCard from "@/components/board/MemoCard";
import type { BookmarkPostitItem } from "@/types/board";
import styles from "./page.module.css";

// "DD / MM / YY" 형식으로 날짜 문자열을 변환 (예: "2026-03-25T10:00:00" → "25 / 03 / 26")
function formatDate(iso: string): string {
  const d = new Date(iso);
  const dd = String(d.getDate()).padStart(2, "0");
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const yy = String(d.getFullYear()).slice(2);
  return `${dd} / ${mm} / ${yy}`;
}

// BookmarkPostitItem을 MemoCard가 받을 수 있는 props 형태로 변환
function toMemoCardProps(item: BookmarkPostitItem) {
  return {
    id: item.sourceId,
    // answerKeyword를 카드 기본 화면의 타이틀로 사용 (짧은 키워드)
    title: item.answerKeyword,
    // 질문 원문은 호버 시 Q&A 영역에 표시
    category: "Bookmark",
    date: formatDate(item.createdAt),
    colorCode: item.layout.colorCode,
    rotationDeg: item.layout.rotationDeg,
    positionX: item.layout.positionX,
    positionY: item.layout.positionY,
    // 호버 시 표시할 Q&A 쌍 (MemoCard의 splitLabel이 "Q. "/"A. " 접두사를 파싱함)
    qnaItems: [
      { question: `Q. ${item.question}`, answer: `A. ${item.answer}` },
    ],
  };
}

// params를 통해 URL에 있는 userUuid 가져오기 가능
export default function BoardPage({
  params,
}: {
  params: Promise<{ userUuid: string }>;
}) {
  // 비동기인 params를 React.use()로 풀어줌
  const resolvedParams = use(params);
  const userUuid = resolvedParams.userUuid;

  // 보드 기본 정보 조회 (isOwner, ownerNickname 등)
  const { data: boardInfo } = useShareBoardInfo(userUuid);
  const isOwner = boardInfo?.isOwner ?? false;

  const { setBoardInfo, clearBoardInfo } = useBoardStore();

  // API 응답이 도착하면 스토어에 저장, 페이지 이탈 시 초기화
  useEffect(() => {
    if (boardInfo) setBoardInfo(boardInfo);
    return () => clearBoardInfo();
  }, [boardInfo, setBoardInfo, clearBoardInfo]);

  // 북마크 탭 포스트잇 목록 (무한 스크롤 지원)
  const {
    data: bookmarkData,
    isLoading: isBookmarkLoading,
  } = useBookmarkPostits(userUuid);

  // 모든 페이지의 items를 하나의 배열로 합침
  const bookmarkItems = bookmarkData?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className={styles.page}>
      <header className={styles.stickyHeader}>
        <div className={styles.headerInner}>
          <span className={styles.headerTitle}>
            {isOwner ? "나의 핀" : `${boardInfo?.ownerNickname ?? ""}의 핀`}
          </span>
          <span className={styles.headerCount}>{bookmarkItems.length} items</span>
        </div>

        {/* 탭 버튼: 현재 북마크 탭만 활성화 */}
        <div className={styles.tabBar}>
          <button className={`${styles.tab} ${styles.tabActive}`}>
            My Q&amp;A Memos
          </button>
          <button className={styles.tab}>Question Memos</button>
        </div>
      </header>

      {/* 북마크 포스트잇 캔버스 */}
      <div className={styles.canvas}>
        {isBookmarkLoading && (
          <p className={styles.emptyMsg}>불러오는 중...</p>
        )}
        {!isBookmarkLoading && bookmarkItems.length === 0 && (
          <p className={styles.emptyMsg}>북마크한 포스트잇이 없습니다.</p>
        )}
        {bookmarkItems.map((item) => {
          const props = toMemoCardProps(item);
          return <MemoCard key={props.id} {...props} />;
        })}
      </div>
    </div>
  );
}

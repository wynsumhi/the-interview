"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useShareBoardInfo } from "@/hooks/useShareBoardInfo";
import { useBookmarkPostits } from "@/hooks/useBookmarkPostits";
import { useQuestionPostits } from "@/hooks/useQuestionPostits";
import { useCreateQuestion } from "@/hooks/useCreateQuestion";
import { useUpdateQuestion } from "@/hooks/useUpdateQuestion";
import { useDeleteQuestion } from "@/hooks/useDeleteQuestion";
import { useBoardStore } from "@/store/useBoardStore";
import MemoCard from "@/components/board/MemoCard";
import AddQuestionCard from "@/components/board/AddQuestionCard";
import type { BookmarkPostitItem, QuestionPostitItem } from "@/types/board";
import styles from "./page.module.css";

type TabType = "bookmarks" | "questions";

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

// QuestionPostitItem을 MemoCard가 받을 수 있는 props 형태로 변환
function toQuestionMemoCardProps(item: QuestionPostitItem) {
  return {
    id: item.questionId,
    // 질문 내용을 카드 기본 화면의 타이틀로 사용
    title: item.content,
    category: "Question",
    // 수정된 경우 updatedAt을 표시, 그렇지 않으면 createdAt 사용
    date: formatDate(item.updatedAt ?? item.createdAt),
    colorCode: item.layout.colorCode,
    rotationDeg: item.layout.rotationDeg,
    positionX: item.layout.positionX,
    positionY: item.layout.positionY,
    // 호버 시 질문 내용과 현재 상태 레이블을 표시
    qnaItems: [
      { question: `Q. ${item.content}`, answer: item.statusLabel },
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
  const router = useRouter();

  // 활성 탭 상태 관리 ("bookmarks" | "questions")
  const [activeTab, setActiveTab] = useState<TabType>("bookmarks");

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

  // 질문 탭 포스트잇 목록 (무한 스크롤 지원)
  const {
    data: questionData,
    isLoading: isQuestionLoading,
  } = useQuestionPostits(userUuid);

  // 질문 생성 뮤테이션: 성공 시 questionPostits 캐시 자동 무효화
  const { mutate: submitQuestion, isPending: isSubmitting } = useCreateQuestion(userUuid);

  // 질문 수정 뮤테이션: 성공 시 캐시의 content/updatedAt을 즉시 갱신
  const { mutate: submitEdit, isPending: isSavingEdit } = useUpdateQuestion(userUuid);

  // 질문 삭제 뮤테이션: 성공 시 캐시에서 해당 항목 즉시 제거
  const { mutate: submitDelete } = useDeleteQuestion(userUuid);

  // 질문 포스트잇 기반으로 인터뷰 시작: conversation 페이지로 이동
  // questionId를 URL 파라미터로 전달하면 conversation 페이지에서 start-interview API를 호출함
  const handleStartInterview = (questionId: number) => {
    router.push(`/interview/conversation?questionId=${questionId}`);
  };

  // 모든 페이지의 items를 하나의 배열로 합침
  // 공유 허용(shareRange) 필터링은 백엔드 JPQL 쿼리에서 처리하므로 프론트 추가 필터 불필요
  const bookmarkItems = bookmarkData?.pages.flatMap((page) => page.items) ?? [];
  const questionItems = questionData?.pages.flatMap((page) => page.items) ?? [];

  const isLoading = activeTab === "bookmarks" ? isBookmarkLoading : isQuestionLoading;
  const itemCount = activeTab === "bookmarks" ? bookmarkItems.length : questionItems.length;

  return (
    <div className={styles.page}>
      <header className={styles.stickyHeader}>
        <div className={styles.headerInner}>
          <span className={styles.headerTitle}>
            {isOwner ? "나의 핀" : `${boardInfo?.ownerNickname ?? ""}의 핀`}
          </span>
          <span className={styles.headerCount}>{itemCount} items</span>
        </div>

        {/* 탭 버튼: 클릭 시 해당 탭으로 전환 */}
        <div className={styles.tabBar}>
          <button
            className={`${styles.tab} ${activeTab === "bookmarks" ? styles.tabActive : ""}`}
            onClick={() => setActiveTab("bookmarks")}
          >
            My Q&amp;A Memos
          </button>
          <button
            className={`${styles.tab} ${activeTab === "questions" ? styles.tabActive : ""}`}
            onClick={() => setActiveTab("questions")}
          >
            Question Memos
          </button>
        </div>
      </header>

      {/* 포스트잇 캔버스: 활성 탭에 따라 다른 항목 렌더링 */}
      <div className={styles.canvas}>
        {isLoading && (
          <p className={styles.emptyMsg}>불러오는 중...</p>
        )}

        {/* 북마크 탭 */}
        {!isLoading && activeTab === "bookmarks" && bookmarkItems.length === 0 && (
          <p className={styles.emptyMsg}>북마크한 포스트잇이 없습니다.</p>
        )}
        {activeTab === "bookmarks" &&
          bookmarkItems.map((item) => {
            const props = toMemoCardProps(item);
            return <MemoCard key={props.id} {...props} />;
          })}

        {/* 질문 탭 */}
        {activeTab === "questions" && (
          <AddQuestionCard
            onSubmit={submitQuestion}
            isSubmitting={isSubmitting}
          />
        )}
        {!isLoading && activeTab === "questions" && questionItems.length === 0 && (
          <p className={styles.emptyMsg}>받은 질문 포스트잇이 없습니다.</p>
        )}
        {activeTab === "questions" &&
          questionItems.map((item) => {
            const props = toQuestionMemoCardProps(item);
            // questionStatus가 "USED"이면 이미 인터뷰가 진행된 질문
            // 해당 경우 버튼을 숨기고 카드 투명도를 낮춰 비활성 상태를 표시
            const canStart = item.status !== "USED";
            return (
              <MemoCard
                key={props.id}
                {...props}
                disableHover
                onAction={canStart ? () => handleStartInterview(item.questionId) : undefined}
                actionLabel={canStart ? "Start Interview" : undefined}
                dimmed={!canStart}
                canEdit={item.permissions.canEditContent}
                onSaveEdit={(content) => submitEdit({ questionId: item.questionId, content })}
                isSaving={isSavingEdit}
                canDelete={item.permissions.canDelete}
                onDelete={() => submitDelete(item.questionId)}
              />
            );
          })}
      </div>
    </div>
  );
}

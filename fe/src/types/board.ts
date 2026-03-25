/** GET /v1/share-board/member/{userUuid} 응답의 result 필드 */
export interface ShareBoardInfo {
  isOwner: boolean;
  ownerMemberId: number;
  ownerNickname: string;
  ownerUserUuid: string;
  shareBoardId: number;
}

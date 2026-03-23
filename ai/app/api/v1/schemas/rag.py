from pydantic import BaseModel, Field


class InterviewRAGTurnRequest(BaseModel):
    """RAG 적재에 사용하는 개별 문답 모델."""

    question_id: str = Field(min_length=1)
    answer_id: str = Field(min_length=1)
    question: str = Field(min_length=1)
    answer: str = Field(min_length=1)
    turn_index: int = Field(ge=1)
    metadata: dict[str, str] = Field(default_factory=dict)


class InterviewRAGUpsertRequest(BaseModel):
    """단일 인터뷰를 사용자 RAG에 추가 적재할 때 사용하는 요청 모델."""

    user_id: str = Field(min_length=1)
    interview_id: str = Field(min_length=1)
    turns: list[InterviewRAGTurnRequest] = Field(min_length=1)
    metadata: dict[str, str] = Field(default_factory=dict)


class InterviewRAGInterviewRequest(BaseModel):
    """사용자 전체 RAG 재구성 시 개별 인터뷰 입력 모델."""

    interview_id: str = Field(min_length=1)
    turns: list[InterviewRAGTurnRequest] = Field(min_length=1)
    metadata: dict[str, str] = Field(default_factory=dict)


class InterviewRAGUpsertResponse(BaseModel):
    """인터뷰 RAG upsert 응답 모델."""

    status: str
    message: str
    collection_name: str
    indexed_count: int


class InterviewRAGDeleteRequest(BaseModel):
    """인터뷰 RAG 삭제 요청 모델."""

    user_id: str = Field(min_length=1)
    interview_id: str = Field(min_length=1)


class InterviewRAGDeleteResponse(BaseModel):
    """인터뷰 RAG 삭제 응답 모델."""

    status: str
    message: str
    collection_name: str
    deleted_count: int


class InterviewRAGReindexRequest(BaseModel):
    """사용자 전체 인터뷰 RAG 재구성 요청 모델."""

    user_id: str = Field(min_length=1)
    interviews: list[InterviewRAGInterviewRequest] = Field(min_length=1)


class InterviewRAGReindexResponse(BaseModel):
    """사용자 전체 인터뷰 RAG 재구성 응답 모델."""

    status: str
    message: str
    collection_name: str
    reindexed_interview_count: int
    reindexed_turn_count: int

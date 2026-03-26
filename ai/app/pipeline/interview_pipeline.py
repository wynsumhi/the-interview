from __future__ import annotations

import concurrent.futures
import logging
from dataclasses import asdict, dataclass

import requests
from app.pipeline.pii_masking import build_guardrail_payload
from app.prompts.interview_prompts import (
    FIRST_INTERVIEW_QUESTION_MAP,
    FIRST_INTERVIEW_SYSTEM_PROMPT,
    INTERVIEW_SYSTEM_PROMPT,
    MAGAZINE_GENERATION_SYSTEM_PROMPT,
)
from app.services.backend_callback_service import save_refined_answer_to_backend
from app.services.interview_state_service import (
    InterviewStateDbPayload,
    InterviewStatePromptPayload,
    analyze_interview_state,
    build_interview_state_db_payload,
    build_interview_state_prompt_payload,
)
from app.services.llm_service import create_message
from app.services.rag_service import (
    format_retrieved_turns_for_prompt,
    retrieve_similar_interview_history,
)
from app.services.stt_service import speech_to_text
from app.services.tts_service import text_to_speech

logger = logging.getLogger(__name__)
_REFINEMENT_EXECUTOR = concurrent.futures.ThreadPoolExecutor(max_workers=4)


@dataclass(frozen=True)
class InterviewPipelineResult:
    """인터뷰 파이프라인 한 번의 실행 결과를 묶는 DTO다.

    파이프라인 내부에서는 상세 dataclass를 유지하고,
    API 응답 직전에는 `to_response_dict()`로 직렬화 가능한 형태로 변환한다.
    """

    stt_text: str
    masked_stt_text: str
    pii_masking_duration_seconds: float
    interview_state_db: InterviewStateDbPayload
    interview_state_prompt: InterviewStatePromptPayload
    llm_text: str
    tts_audio: bytes
    refined_answer: str

    def to_response_dict(self) -> dict[str, object]:
        """서비스 계층에서 바로 API 응답으로 바꿀 수 있게 dict로 직렬화한다."""

        return {
            "stt_text": self.stt_text,
            "masked_stt_text": self.masked_stt_text,
            "pii_masking_duration_seconds": self.pii_masking_duration_seconds,
            "interview_state_db": asdict(self.interview_state_db),
            "interview_state_prompt": asdict(self.interview_state_prompt),
            "question_text": self.llm_text,
            "tts_audio": self.tts_audio,
            "refined_answer": self.refined_answer,
        }


def _build_regular_messages(
    stt_text: str,
    prompt_payload: InterviewStatePromptPayload,
    rag_context: str = "",
) -> list[dict[str, str]]:
    """일반 인터뷰 단계에서 사용할 사용자 메시지를 구성한다.

    감정 상태 블록은 항상 포함하고,
    rag_context가 있을 때만 과거 인터뷰 참고 블록을 포함하여 LLM에 전달한다.
    """

    sections = [prompt_payload.formatted_prompt]
    if rag_context:
        sections.append(f"[유사한 과거 인터뷰 참고]\n{rag_context}")
    sections.append(f"[사용자 답변]\n{stt_text}")
    return [{"role": "user", "content": "\n\n".join(sections)}]


def _build_first_interview_messages(
    stt_text: str,
    nickname: str,
    sequence: int,
    previous_question: str | None,
    prompt_payload: InterviewStatePromptPayload,
) -> list[dict[str, str]]:
    """첫 인터뷰 단계에서 사용할 사용자 메시지를 구성한다.

    첫 인터뷰는 현재 순서와 직전 질문 맥락이 중요하므로,
    닉네임과 질문 순서를 함께 묶어 LLM에 전달한다.
    """

    if sequence == 1:
        context = "첫 질문이므로 이전 대화는 없습니다."
    else:
        context = f"직전 질문:\n{previous_question}\n\n사용자 응답:\n{stt_text}"

    user_message = (
        f"사용자 닉네임: {nickname}\n"
        f"현재 진행 중인 질문: {FIRST_INTERVIEW_QUESTION_MAP[sequence]}\n\n"
        f"{prompt_payload.formatted_prompt}\n\n"
        f"대화 맥락:\n{context}"
    )
    return [{"role": "user", "content": user_message}]


def _build_magazine_refinement_messages(stt_text: str) -> list[dict[str, str]]:
    """
    LLM이 인터뷰 답변을 잡지 기사 스타일로 다듬도록 하는 메시지를 구성한다.
    """

    user_message = (
        "[Task]\n"
        "Refine the following interview answer into magazine style.\n"
        "Do not answer the speaker's question. Do not add guidance or refusal.\n\n"
        "[Interview Answer]\n"
        f"{stt_text}"
    )
    return [{"role": "user", "content": user_message}]


def _run_refinement_and_callback(
    *,
    magazine_kwargs: dict[str, object],
    interview_id: int | None,
    question_id: int | None,
) -> str:
    refined_answer = create_message(**magazine_kwargs)

    if interview_id is not None and question_id is not None:
        try:
            save_refined_answer_to_backend(
                interview_id=interview_id,
                question_id=question_id,
                refined_answer=refined_answer,
            )
        except requests.RequestException:
            logger.exception(
                "Failed to save refined answer to backend.",
                extra={
                    "interview_id": interview_id,
                    "question_id": question_id,
                },
            )

    return refined_answer


def _log_background_refinement_failure(
    future: concurrent.futures.Future[str],
) -> None:
    try:
        future.result()
    except Exception:  # noqa: BLE001
        logger.exception("Background refinement task failed.")


def run_interview_pipeline(
    audio_file_path: str,
    is_first_finished: bool,
    interview_id: int | None = None,
    question_id: int | None = None,
    voice: str | None = None,
    user_id: str | None = None,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> InterviewPipelineResult:
    """STT부터 상태 분석, 꼬리질문 생성, TTS까지 한 번에 실행한다."""

    stt_text = speech_to_text(audio_file_path, label="primary")
    return run_interview_pipeline_from_stt_text(
        audio_file_path=audio_file_path,
        stt_text=stt_text,
        refinement_stt_text=stt_text,
        is_first_finished=is_first_finished,
        interview_id=interview_id,
        question_id=question_id,
        voice=voice,
        user_id=user_id,
        sequence=sequence,
        nickname=nickname,
        previous_question=previous_question,
        max_tokens=max_tokens,
    )


def run_interview_pipeline_from_stt_text(
    audio_file_path: str,
    stt_text: str,
    is_first_finished: bool,
    refinement_stt_text: str | None = None,
    interview_id: int | None = None,
    question_id: int | None = None,
    voice: str | None = None,
    user_id: str | None = None,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> InterviewPipelineResult:
    """이미 확보한 STT 텍스트를 기준으로 나머지 인터뷰 단계를 실행한다."""

    guardrail_payload = build_guardrail_payload(stt_text)
    interview_state = analyze_interview_state(audio_file_path, stt_text)
    interview_state_db = build_interview_state_db_payload(interview_state)
    interview_state_prompt = build_interview_state_prompt_payload(interview_state)
    normalized_refinement_stt_text = (
        refinement_stt_text.strip()
        if refinement_stt_text is not None
        else speech_to_text(audio_file_path, label="refinement")
    )

    _magazine_kwargs = {
        "messages": _build_magazine_refinement_messages(normalized_refinement_stt_text),
        "system": MAGAZINE_GENERATION_SYSTEM_PROMPT,
        "max_tokens": 300,
        "label": "answer_refinement",
    }

    # 첫 인터뷰의 마지막 답변까지 도달하면 추가 질문은 만들지 않는다.
    # 매거진 정제는 마지막 답변에도 그대로 수행한다.
    if not is_first_finished and (sequence or 1) >= 6:
        refined_answer = create_message(**_magazine_kwargs)
        return InterviewPipelineResult(
            stt_text=stt_text,
            masked_stt_text=guardrail_payload.masked_text,
            pii_masking_duration_seconds=guardrail_payload.masking_result.duration_seconds,
            interview_state_db=interview_state_db,
            interview_state_prompt=interview_state_prompt,
            llm_text="",
            tts_audio=b"",
            refined_answer=refined_answer,
        )

    if not is_first_finished:
        system = FIRST_INTERVIEW_SYSTEM_PROMPT
        messages = _build_first_interview_messages(
            stt_text=guardrail_payload.masked_text,
            nickname=nickname or "",
            sequence=sequence or 1,
            previous_question=previous_question,
            prompt_payload=interview_state_prompt,
        )
    else:
        rag_context = ""
        if (user_id or "").strip() and (previous_question or "").strip():
            retrieved_turns = retrieve_similar_interview_history(
                user_id=user_id or "",
                current_question=previous_question or "",
                current_answer=guardrail_payload.masked_text,
            )
            rag_context = format_retrieved_turns_for_prompt(retrieved_turns)

        system = INTERVIEW_SYSTEM_PROMPT
        messages = _build_regular_messages(
            stt_text=guardrail_payload.masked_text,
            prompt_payload=interview_state_prompt,
            rag_context=rag_context,
        )

    # 후속 질문 생성과 매거진 정제를 병렬로 실행한다.
    # 두 작업 모두 LLM 호출이므로 ThreadPoolExecutor로 동시에 처리한다.
    refinement_future = _REFINEMENT_EXECUTOR.submit(
        _run_refinement_and_callback,
        magazine_kwargs=_magazine_kwargs,
        interview_id=interview_id,
        question_id=question_id,
    )

    llm_text = create_message(
        messages=messages,
        system=system,
        max_tokens=max_tokens,
        label="followup_question",
    )

    if interview_id is not None and question_id is not None:
        refined_answer = ""
        refinement_future.add_done_callback(_log_background_refinement_failure)
    else:
        refined_answer = refinement_future.result()

    tts_audio = text_to_speech(llm_text, voice=voice)

    return InterviewPipelineResult(
        stt_text=stt_text,
        masked_stt_text=guardrail_payload.masked_text,
        pii_masking_duration_seconds=guardrail_payload.masking_result.duration_seconds,
        interview_state_db=interview_state_db,
        interview_state_prompt=interview_state_prompt,
        llm_text=llm_text,
        tts_audio=tts_audio,
        refined_answer=refined_answer,
    )

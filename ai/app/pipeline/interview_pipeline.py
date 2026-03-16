from __future__ import annotations

from dataclasses import dataclass

from app.prompts.interview_prompts import (
    FIRST_INTERVIEW_QUESTION_MAP,
    FIRST_INTERVIEW_SYSTEM_PROMPT,
    INTERVIEW_SYSTEM_PROMPT,
)
from app.services.llm_service import create_message
from app.services.stt_service import speech_to_text
from app.services.tts_service import text_to_speech


@dataclass(frozen=True)
class InterviewPipelineResult:
    # 매거진 인터뷰 흐름의 단계별 결과를 함께 반환해 후속 단계 확장을 쉽게 만든다.
    stt_text: str
    llm_text: str
    tts_audio: bytes


def _build_regular_messages(stt_text: str) -> list[dict[str, str]]:
    return [{"role": "user", "content": stt_text}]


def _build_first_interview_messages(
    stt_text: str,
    nickname: str,
    sequence: int,
    previous_question: str | None,
) -> list[dict[str, str]]:
    if sequence == 1:
        context = "첫 질문이므로 이전 대화 없음."
    else:
        context = f"직전 질문:\n{previous_question}\n\n사용자 답변:\n{stt_text}"
    user_message = (
        f"사용자 닉네임: {nickname}\n"
        f"현재 진행할 질문: {FIRST_INTERVIEW_QUESTION_MAP[sequence]}\n\n"
        f"대화 맥락:\n{context}"
    )
    return [{"role": "user", "content": user_message}]


def run_interview_pipeline(
    audio_file_path: str,
    is_first_finished: bool = True,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> InterviewPipelineResult:
    # STT -> LLM -> TTS 순서로 실행한다.
    # is_first_finished=False이면 최초 인터뷰, True이면 일반 인터뷰 프롬프트를 사용한다.
    stt_text = speech_to_text(audio_file_path)

    if not is_first_finished:
        system = FIRST_INTERVIEW_SYSTEM_PROMPT
        messages = _build_first_interview_messages(
            stt_text=stt_text,
            nickname=nickname or "",
            sequence=sequence or 1,
            previous_question=previous_question,
        )
    else:
        system = INTERVIEW_SYSTEM_PROMPT
        messages = _build_regular_messages(stt_text)

    llm_text = create_message(
        messages=messages,
        system=system,
        max_tokens=max_tokens,
    )
    tts_audio = text_to_speech(llm_text)
    return InterviewPipelineResult(
        stt_text=stt_text,
        llm_text=llm_text,
        tts_audio=tts_audio,
    )

from __future__ import annotations

from dataclasses import dataclass

from app.prompts.interview_prompts import INTERVIEW_SYSTEM_PROMPT
from app.services.llm_service import create_message
from app.services.stt_service import speech_to_text
from app.services.tts_service import text_to_speech


@dataclass(frozen=True)
class InterviewPipelineResult:
    # 파이프라인 단계별 결과를 함께 반환해 후속 단계 확장을 쉽게 만든다.
    stt_text: str
    llm_text: str
    tts_audio: bytes


def build_interview_messages(stt_text: str) -> list[dict[str, str]]:
    # 이후 guardrail이나 대화 히스토리가 들어와도 이 함수에서 메시지 조립 규칙을 확장하면 된다.
    return [
        {
            "role": "user",
            "content": stt_text,
        }
    ]


def run_interview_pipeline(
    audio_file_path: str,
    system_prompt: str = INTERVIEW_SYSTEM_PROMPT,
    max_tokens: int = 1024,
) -> InterviewPipelineResult:
    # 현재 단계에서는 guardrail 없이 STT -> LLM -> TTS만 직선으로 연결한다.
    stt_text = speech_to_text(audio_file_path)
    llm_text = create_message(
        messages=build_interview_messages(stt_text),
        system=system_prompt,
        max_tokens=max_tokens,
    )
    tts_audio = text_to_speech(llm_text)
    return InterviewPipelineResult(
        stt_text=stt_text,
        llm_text=llm_text,
        tts_audio=tts_audio,
    )

from __future__ import annotations

from dataclasses import asdict, dataclass

from app.prompts.interview_prompts import (
    FIRST_INTERVIEW_QUESTION_MAP,
    FIRST_INTERVIEW_SYSTEM_PROMPT,
    INTERVIEW_SYSTEM_PROMPT,
)
from app.services.interview_state_service import (
    InterviewStateDbPayload,
    InterviewStatePromptPayload,
    analyze_interview_state,
    build_interview_state_db_payload,
    build_interview_state_prompt_payload,
)
from app.services.llm_service import create_message
from app.services.stt_service import speech_to_text
from app.services.tts_service import text_to_speech


@dataclass(frozen=True)
class InterviewPipelineResult:
    """인터뷰 파이프라인 한 번의 실행 결과를 묶는 DTO다.

    파이프라인 내부에서는 상세 dataclass를 유지하고,
    API 응답 직전에는 `to_response_dict()`로 직렬화 가능한 형태로 변환한다.
    """

    stt_text: str
    interview_state_db: InterviewStateDbPayload
    interview_state_prompt: InterviewStatePromptPayload
    llm_text: str
    tts_audio: bytes

    def to_response_dict(self) -> dict[str, object]:
        """서비스 계층에서 바로 API 응답으로 바꿀 수 있게 dict로 직렬화한다."""

        return {
            "stt_text": self.stt_text,
            "interview_state_db": asdict(self.interview_state_db),
            "interview_state_prompt": asdict(self.interview_state_prompt),
            "question_text": self.llm_text,
            "tts_audio": self.tts_audio,
        }


def _build_regular_messages(
    stt_text: str,
    prompt_payload: InterviewStatePromptPayload,
) -> list[dict[str, str]]:
    """일반 인터뷰 단계에서 사용할 사용자 메시지를 구성한다.

    기존 시스템 프롬프트는 그대로 유지하고,
    사용자 메시지 안에 참고용 인터뷰 상태 점수와 실제 답변 텍스트를 함께 넣는다.
    """

    user_message = f"{prompt_payload.formatted_prompt}\n\n[사용자 답변]\n{stt_text}"
    return [{"role": "user", "content": user_message}]


def _build_first_interview_messages(
    stt_text: str,
    nickname: str,
    sequence: int,
    previous_question: str | None,
    prompt_payload: InterviewStatePromptPayload,
) -> list[dict[str, str]]:
    """첫 인터뷰 전용 사용자 메시지를 구성한다.

    첫 인터뷰 단계에서는 단순 답변 텍스트만으로는 맥락이 부족하므로,
    현재 질문 번호, 직전 질문, 사용자 닉네임까지 함께 묶어 전달한다.
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


def run_interview_pipeline(
    audio_file_path: str,
    answer_id: int,
    is_first_finished: bool,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> InterviewPipelineResult:
    """STT부터 상태 분석, 후속 질문 생성, TTS까지 한 번에 오케스트레이션한다.

    이 파이프라인은 답변 음성 하나를 입력으로 받아 다음 순서로 처리한다.
    1. STT 수행
    2. 인터뷰 상태 상세 분석 수행
    3. DB 저장용 payload 생성
    4. LLM 참고용 payload 생성
    5. 후속 질문 생성
    6. 생성된 질문 TTS 변환
    """

    stt_text = speech_to_text(audio_file_path)
    return run_interview_pipeline_from_stt_text(
        audio_file_path=audio_file_path,
        stt_text=stt_text,
        answer_id=answer_id,
        is_first_finished=is_first_finished,
        sequence=sequence,
        nickname=nickname,
        previous_question=previous_question,
        max_tokens=max_tokens,
    )


def run_interview_pipeline_from_stt_text(
    audio_file_path: str,
    stt_text: str,
    answer_id: int,
    is_first_finished: bool,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> InterviewPipelineResult:
    """STT 寃곌낵瑜?直接 注入받아 STT 단계를 건너뛴 파이프라인을 실행한다."""

    interview_state = analyze_interview_state(audio_file_path, stt_text)
    interview_state_db = build_interview_state_db_payload(answer_id, interview_state)
    interview_state_prompt = build_interview_state_prompt_payload(interview_state)

    # 첫 인터뷰의 마지막 답변까지 도달하면 더 이상 후속 질문을 생성하지 않는다.
    # 이 경우에도 STT와 상태 분석 결과는 반환해서 백엔드가 저장/후처리에 활용할 수 있다.
    if not is_first_finished and (sequence or 1) >= 6:
        return InterviewPipelineResult(
            stt_text=stt_text,
            interview_state_db=interview_state_db,
            interview_state_prompt=interview_state_prompt,
            llm_text="",
            tts_audio=b"",
        )

    if not is_first_finished:
        system = FIRST_INTERVIEW_SYSTEM_PROMPT
        messages = _build_first_interview_messages(
            stt_text=stt_text,
            nickname=nickname or "",
            sequence=sequence or 1,
            previous_question=previous_question,
            prompt_payload=interview_state_prompt,
        )
    else:
        system = INTERVIEW_SYSTEM_PROMPT
        messages = _build_regular_messages(
            stt_text=stt_text,
            prompt_payload=interview_state_prompt,
        )

    llm_text = create_message(
        messages=messages,
        system=system,
        max_tokens=max_tokens,
    )
    tts_audio = text_to_speech(llm_text)

    return InterviewPipelineResult(
        stt_text=stt_text,
        interview_state_db=interview_state_db,
        interview_state_prompt=interview_state_prompt,
        llm_text=llm_text,
        tts_audio=tts_audio,
    )

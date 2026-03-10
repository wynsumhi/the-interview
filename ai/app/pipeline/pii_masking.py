from __future__ import annotations

from dataclasses import dataclass

from app.services.guardrail_service import MaskingResult, mask_stt_text_for_llm


@dataclass(frozen=True)
class GuardrailPipelinePayload:
    """STT 이후 단계가 공유할 수 있는 guardrail 결과 묶음이다.

    `stt_text`는 원본 보존용,
    `masked_text`는 실제 LLM 프롬프트 입력용,
    `masking_result`는 상세 추적 및 감사용 메타데이터 용도로 둔다.
    """

    stt_text: str
    masked_text: str
    masking_result: MaskingResult


def build_guardrail_payload(stt_text: str) -> GuardrailPipelinePayload:
    """STT 결과를 받아 guardrail 단계의 표준 payload로 변환한다.

    파이프라인이 완성되면 아래 흐름으로 바로 연결하면 된다.
    `speech -> STT -> build_guardrail_payload(stt_text) -> payload.masked_text -> LLM`

    이렇게 래핑해 두면 이후에 emotion 결과, 정책 태그, 추가 guardrail 판단값이
    필요해져도 함수 시그니처를 크게 흔들지 않고 확장할 수 있다.
    """
    masking_result = mask_stt_text_for_llm(stt_text)
    return GuardrailPipelinePayload(
        stt_text=stt_text,
        masked_text=masking_result.masked_text,
        masking_result=masking_result,
    )

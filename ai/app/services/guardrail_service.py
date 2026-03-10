from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from time import perf_counter

# STT가 반환한 원문 텍스트에서 정형화된 개인정보 패턴을 먼저 탐지한다.
# 현재는 전화번호, 이메일, 주민등록번호, 계좌번호, 카드번호를 규칙 기반으로 처리한다.
# 이 규칙 목록은 나중에 모델 기반 분류기나 추가 패턴이 붙더라도 교체하기 쉽도록
# 서비스 레벨 상수로 분리해 둔 것이다.
PII_PATTERNS: list[tuple[str, re.Pattern[str], str]] = [
    (
        "PHONE",
        re.compile(r"(?<!\d)(?:01[0-9]-\d{3,4}-\d{4}|0[2-6][0-5]?-\d{3,4}-\d{4})(?!\d)"),
        "rule:phone",
    ),
    (
        "EMAIL",
        re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
        "rule:email",
    ),
    (
        "RRN",
        re.compile(r"(?<!\d)\d{6}-[1-4]\d{6}(?!\d)"),
        "rule:resident-registration-number",
    ),
    (
        "ACCOUNT",
        re.compile(
            r"(?:(?:계좌|통장|은행|농협|신한|국민|하나|우리|기업|카카오뱅크|토스뱅크|새마을금고|우체국|수협|부산|대구)\s*)?"
            r"(?<!\d)\d{2,6}-\d{2,6}-\d{4,8}(?!\d)"
        ),
        "rule:bank-account",
    ),
    (
        "CARD",
        re.compile(r"(?<!\d)\d{4}-\d{4}-\d{4}-\d{4}(?!\d)"),
        "rule:card-number",
    ),
]


@dataclass(frozen=True)
class MaskedEntity:
    """한 번의 마스킹 과정에서 탐지된 개인정보 한 건을 표현한다."""

    entity_type: str
    original: str
    replacement: str
    start: int
    end: int
    source: str


@dataclass(frozen=True)
class MaskingResult:
    """원문, 마스킹 결과, 탐지된 엔티티 목록을 함께 반환하는 결과 객체다."""

    original_text: str
    masked_text: str
    entities: list[MaskedEntity]
    duration_seconds: float


@dataclass(frozen=True)
class CandidateSpan:
    """정규식 매칭 단계에서 수집한 후보 구간 정보를 담는다."""

    text: str
    entity_type: str
    start: int
    end: int
    source: str


def mask_sensitive_information(text: str) -> MaskingResult:
    """입력 텍스트를 순회하며 개인정보를 치환하고 LLM 전달용 결과를 만든다.

    동작 순서는 다음과 같다.
    1. 전체 텍스트에서 정규식 규칙으로 개인정보 후보를 수집한다.
    2. 겹치는 후보가 있으면 더 긴 범위를 남겨 중복 치환을 방지한다.
    3. 발견 순서대로 `[PHONE_1]`, `[EMAIL_1]` 같은 토큰으로 치환한다.
    4. 원문, 치환 결과, 탐지 메타데이터, 처리 시간을 묶어서 반환한다.
    """
    start_time = perf_counter()
    candidates = _collect_candidates(text)
    entities: list[MaskedEntity] = []
    counters: dict[str, int] = {}
    masked_parts: list[str] = []
    cursor = 0

    for candidate in candidates:
        # 이미 앞선 후보가 같은 구간을 덮었다면 현재 후보는 무시
        if candidate.start < cursor:
            continue

        counters[candidate.entity_type] = counters.get(candidate.entity_type, 0) + 1
        replacement = f"[{candidate.entity_type}_{counters[candidate.entity_type]}]"

        # 직전 커서부터 현재 후보 시작점 전까지는 원문 그대로 유지한다.
        masked_parts.append(text[cursor : candidate.start])
        masked_parts.append(replacement)
        entities.append(
            MaskedEntity(
                entity_type=candidate.entity_type,
                original=candidate.text,
                replacement=replacement,
                start=candidate.start,
                end=candidate.end,
                source=candidate.source,
            )
        )
        # 다음 복사는 현재 후보 끝 지점부터 다시 시작한다.
        cursor = candidate.end

    masked_parts.append(text[cursor:])

    return MaskingResult(
        original_text=text,
        masked_text="".join(masked_parts),
        entities=entities,
        duration_seconds=round(perf_counter() - start_time, 4),
    )


def mask_stt_text_for_llm(stt_text: str) -> MaskingResult:
    """향후 파이프라인에서 STT 직후 바로 호출할 수 있도록 만든 진입점이다.

    지금은 내부적으로 `mask_sensitive_information()`을 그대로 호출하지만,
    이후 전처리, 로깅, 정책 분기, 모델 기반 마스킹이 추가되더라도
    파이프라인 코드는 이 함수만 의존하면 되도록 의도를 분리해 두었다.
    """
    return mask_sensitive_information(stt_text)


def format_masking_report(result: MaskingResult) -> str:
    """로컬 실행이나 디버깅 시 사람이 읽기 쉬운 문자열 리포트로 변환한다."""
    return (
        f"Original: {result.original_text}\n"
        f"Masked: {result.masked_text}\n"
        f"Masking time: {result.duration_seconds}s\n"
        f"Entities: {
            json.dumps([asdict(entity) for entity in result.entities], ensure_ascii=False)
        }"
    )


def _collect_candidates(text: str) -> list[CandidateSpan]:
    """모든 규칙을 적용해 후보 구간을 모은 뒤 중복 제거 단계로 넘긴다."""
    candidates: list[CandidateSpan] = []
    for entity_type, pattern, source in PII_PATTERNS:
        for match in pattern.finditer(text):
            candidates.append(
                CandidateSpan(
                    text=match.group(0),
                    entity_type=entity_type,
                    start=match.start(),
                    end=match.end(),
                    source=source,
                )
            )
    return _dedupe_candidates(candidates)


def _dedupe_candidates(candidates: list[CandidateSpan]) -> list[CandidateSpan]:
    """겹치는 후보가 있을 때 더 넓은 구간을 우선 유지해 중복 치환을 막는다."""

    # 예를 들어 계좌번호 규칙과 숫자 패턴이 비슷한 다른 규칙이 동시에 잡히면
    # 같은 위치를 두 번 치환할 수 있으므로, 먼저 긴 범위를 남기고 나머지는 버린다.
    deduped: list[CandidateSpan] = []
    for candidate in sorted(candidates, key=lambda item: (item.start, -(item.end - item.start))):
        if any(
            existing.start <= candidate.start and candidate.end <= existing.end
            for existing in deduped
        ):
            continue
        deduped.append(candidate)
    return sorted(deduped, key=lambda item: item.start)

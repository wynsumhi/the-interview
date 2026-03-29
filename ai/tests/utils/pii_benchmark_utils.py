from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from time import perf_counter

TEST_TEXT = (
    "연락은 010-1234-5678 또는 test.user@example.com 으로 주세요. "
    "주민번호는 900101-1234567이고, 국민은행 123-45-678901 계좌로 송금하면 됩니다."
)

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
            r"(?:(?:국민|신한|우리|하나|농협|기업|카카오뱅크|토스뱅크|부산|대구|광주|전북|경남|수협|우체국)\s*)?"
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
    entity_type: str
    original: str
    replacement: str
    start: int
    end: int
    source: str


@dataclass(frozen=True)
class MaskingResult:
    original_text: str
    masked_text: str
    entities: list[MaskedEntity]
    duration_seconds: float


@dataclass(frozen=True)
class CandidateSpan:
    text: str
    entity_type: str
    start: int
    end: int
    source: str


def mask_sensitive_information(text: str) -> MaskingResult:
    start_time = perf_counter()
    candidates = _collect_candidates(text)
    entities: list[MaskedEntity] = []
    counters: dict[str, int] = {}
    masked_parts: list[str] = []
    cursor = 0

    for candidate in candidates:
        if candidate.start < cursor:
            continue

        counters[candidate.entity_type] = counters.get(candidate.entity_type, 0) + 1
        replacement = f"[{candidate.entity_type}_{counters[candidate.entity_type]}]"

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
        cursor = candidate.end

    masked_parts.append(text[cursor:])

    return MaskingResult(
        original_text=text,
        masked_text="".join(masked_parts),
        entities=entities,
        duration_seconds=round(perf_counter() - start_time, 4),
    )


def format_masking_report(result: MaskingResult) -> str:
    return (
        f"Original: {result.original_text}\n"
        f"Masked: {result.masked_text}\n"
        f"Masking time: {result.duration_seconds}s\n"
        f"Entities: {
            json.dumps([asdict(entity) for entity in result.entities], ensure_ascii=False)
        }"
    )


def _collect_candidates(text: str) -> list[CandidateSpan]:
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
    deduped: list[CandidateSpan] = []
    for candidate in sorted(candidates, key=lambda item: (item.start, -(item.end - item.start))):
        if any(
            existing.start <= candidate.start and candidate.end <= existing.end
            for existing in deduped
        ):
            continue
        deduped.append(candidate)
    return sorted(deduped, key=lambda item: item.start)

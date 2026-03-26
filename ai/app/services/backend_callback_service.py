from __future__ import annotations

import logging

import requests

from app.core import settings

logger = logging.getLogger(__name__)


def is_refined_answer_callback_enabled() -> bool:
    return bool(settings.BACKEND_INTERNAL_BASE_URL and settings.BACKEND_INTERNAL_TOKEN)


def save_refined_answer_to_backend(
    *,
    interview_id: int,
    question_id: int,
    refined_answer: str,
) -> bool:
    if not is_refined_answer_callback_enabled():
        logger.info(
            "Skipping refined-answer callback because backend callback settings are missing."
        )
        return False

    response = requests.post(
        f"{settings.BACKEND_INTERNAL_BASE_URL}/api/v1/interview/internal/answers/refined",
        json={
            "interviewId": interview_id,
            "questionId": question_id,
            "refinedAnswer": refined_answer,
        },
        headers={"Authorization": f"Bearer {settings.BACKEND_INTERNAL_TOKEN}"},
        timeout=settings.BACKEND_INTERNAL_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    return True

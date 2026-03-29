from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status

from app.api.v1.schemas import SpeechToTextResponse
from app.core.auth import require_scopes
from app.services.stt_service import speech_to_text_from_upload

router = APIRouter(dependencies=[Depends(require_scopes("stt:invoke"))])


@router.post(
    "",
    response_model=SpeechToTextResponse,
    summary="Transcribe audio with STT only",
)
def transcribe_audio_endpoint(
    audio_file: UploadFile = File(...),
) -> SpeechToTextResponse:
    """Accept a webm upload and return only the STT transcription result."""

    try:
        result = speech_to_text_from_upload(audio_file)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return SpeechToTextResponse.model_validate(result)

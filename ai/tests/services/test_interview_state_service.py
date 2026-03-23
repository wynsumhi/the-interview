from pathlib import Path

import soundfile as sf
from app.services.interview_state_service import (
    _signal,
    analyze_interview_state,
    analyze_text_state,
    build_interview_state_db_payload,
    build_interview_state_prompt_payload,
    fuse_signals,
    read_audio,
)


def test_analyze_text_state_defaults_to_calm_for_plain_statement() -> None:
    signal = analyze_text_state("프로젝트를 진행하고 바쁘게 지내고 있어요.")

    assert signal.top_state == "calm"


def test_analyze_text_state_detects_anxious_keywords() -> None:
    signal = analyze_text_state("인터뷰라 조금 긴장되고 불안해서 걱정됩니다.")

    assert signal.top_state == "anxious"


def test_fuse_signals_prefers_text_with_voice_as_secondary_adjustment() -> None:
    text_signal = _signal({"calm": 0.8, "tense": 0.2}, "text")
    voice_signal = _signal({"tense": 0.7, "calm": 0.3}, "voice")

    fused = fuse_signals(text_signal, voice_signal)

    assert fused.top_state == "calm"


def test_analyze_interview_state_returns_structured_result(tmp_path: Path) -> None:
    audio_path = tmp_path / "sample.wav"
    samples = [0.0] * 16000
    sf.write(audio_path, samples, 16000)

    result = analyze_interview_state(str(audio_path), "프로젝트를 진행하고 바쁘게 지내고 있어요.")

    assert result.text_signal.top_state in {"calm", "tense"}
    assert result.voice_signal.top_state in {"calm", "low"}
    assert result.fused_signal.top_state in {"calm", "tense", "low"}


def test_read_audio_converts_stereo_to_mono_float32(tmp_path: Path) -> None:
    audio_path = tmp_path / "stereo.wav"
    samples = [[0.5, -0.5], [0.25, -0.25], [0.0, 0.0]]
    sf.write(audio_path, samples, 16000)

    mono_samples, sample_rate = read_audio(audio_path)

    assert sample_rate == 16000
    assert mono_samples.dtype.name == "float32"
    assert mono_samples.ndim == 1


def test_analyze_text_state_detects_engaged_keywords() -> None:
    signal = analyze_text_state("그 경험은 정말 재미 있었고 작업에 몰입하는 시간이 좋아요.")

    assert signal.top_state == "engaged"


def test_build_interview_state_db_payload_keeps_fused_scores_and_audio_features(
    tmp_path: Path,
) -> None:
    audio_path = tmp_path / "sample.wav"
    samples = [0.0] * 16000
    sf.write(audio_path, samples, 16000)
    state = analyze_interview_state(str(audio_path), "프로젝트를 진행하고 바쁘게 지내고 있어요.")

    payload = build_interview_state_db_payload(123, state)

    assert payload.answer_id == 123
    assert payload.top_state == state.fused_signal.top_state
    assert payload.fused_scores == state.fused_signal.scores
    assert payload.audio_features == state.audio_features


def test_build_interview_state_prompt_payload_returns_reference_only_prompt(
    tmp_path: Path,
) -> None:
    audio_path = tmp_path / "sample.wav"
    samples = [0.0] * 16000
    sf.write(audio_path, samples, 16000)
    state = analyze_interview_state(
        str(audio_path), "그 경험은 정말 재미 있었고 작업에 몰입하는 시간이 좋아요."
    )

    payload = build_interview_state_prompt_payload(state)

    assert payload.top_state == state.fused_signal.top_state
    assert payload.fused_scores["engaged"] >= 0.0
    assert "[인터뷰 상태 참고]" in payload.formatted_prompt
    assert "보조 정보로만 활용하세요" in payload.formatted_prompt

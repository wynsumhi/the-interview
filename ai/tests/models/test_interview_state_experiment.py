from pathlib import Path

import soundfile as sf

from tests.models.interview_state_experiment import (
    _signal,
    analyze_text_state,
    fuse_signals,
    run_interview_state_experiment,
)


def test_analyze_text_state_defaults_to_calm_for_plain_statement() -> None:
    signal = analyze_text_state("프로젝트를 진행하며 바쁘게 지내고 있어요")

    assert signal.top_state == "calm"


def test_analyze_text_state_detects_anxious_keywords() -> None:
    signal = analyze_text_state("면접이라 너무 긴장되고 불안해서 걱정이 됩니다")

    assert signal.top_state == "anxious"


def test_fuse_signals_prefers_text_with_voice_as_secondary_adjustment() -> None:
    text_signal = _signal({"calm": 0.8, "tense": 0.2}, "text")
    voice_signal = _signal({"tense": 0.7, "calm": 0.3}, "voice")

    fused = fuse_signals(text_signal, voice_signal)

    assert fused.top_state == "calm"


def test_run_interview_state_experiment_returns_structured_result(tmp_path: Path) -> None:
    audio_path = tmp_path / "sample.wav"
    samples = [0.0] * 16000
    sf.write(audio_path, samples, 16000)

    result = run_interview_state_experiment(audio_path, "프로젝트를 진행하며 바쁘게 지내고 있어요")

    assert result.text_signal.top_state in {"calm", "tense"}
    assert result.voice_signal.top_state in {"calm", "low"}
    assert result.fused_signal.top_state in {"calm", "tense", "low"}


def test_analyze_text_state_detects_engaged_keywords() -> None:
    signal = analyze_text_state("그 경험이 정말 의미 있었고 작업에 몰입하는 순간이 좋았어요")

    assert signal.top_state == "engaged"

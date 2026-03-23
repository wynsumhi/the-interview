from tests.models.emotion_experiment import (
    build_profiles,
    canonicalize_label,
    fuse_scores,
    normalize_scores,
)


def test_build_profiles_contains_two_audio_and_one_multimodal_option() -> None:
    profiles = build_profiles()

    assert set(profiles) == {
        "wav2vec2_xlsr_ko_v1",
        "wav2vec2_xlsr_ko_v3",
        "audio_text_fusion",
    }
    assert profiles["wav2vec2_xlsr_ko_v1"].mode == "audio"
    assert profiles["wav2vec2_xlsr_ko_v3"].mode == "audio"
    assert profiles["audio_text_fusion"].mode == "multimodal"


def test_canonicalize_label_maps_korean_and_english_variants() -> None:
    assert canonicalize_label("기쁨") == "joy"
    assert canonicalize_label("happy") == "joy"
    assert canonicalize_label("슬픔") == "sadness"
    assert canonicalize_label("분노") == "anger"
    assert canonicalize_label("불안") == "anxiety"
    assert canonicalize_label("당황") == "surprise"


def test_normalize_scores_merges_aliases_under_common_label_space() -> None:
    scores = normalize_scores(
        {
            "happy": 0.25,
            "기쁨": 0.35,
            "sadness": 0.10,
            "슬픔": 0.20,
            "neutral": 0.10,
        }
    )

    assert scores["joy"] == 0.6
    assert scores["sadness"] == 0.3
    assert scores["neutral"] == 0.1


def test_fuse_scores_combines_audio_and_text_predictions_with_weights() -> None:
    fused = fuse_scores(
        {"joy": 0.7, "neutral": 0.3},
        {"joy": 0.2, "anxiety": 0.8},
        audio_weight=0.65,
        text_weight=0.35,
    )

    assert fused["joy"] == 0.525
    assert fused["anxiety"] == 0.28
    assert fused["neutral"] == 0.195

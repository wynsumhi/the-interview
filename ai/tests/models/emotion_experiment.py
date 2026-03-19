from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf

COMMON_LABELS = (
    "joy",
    "sadness",
    "anger",
    "anxiety",
    "neutral",
    "surprise",
    "hurt",
    "disgust",
    "unknown",
)

LABEL_ALIASES = {
    "joy": "joy",
    "happy": "joy",
    "happiness": "joy",
    "기쁨": "joy",
    "즐거움": "joy",
    "sad": "sadness",
    "sadness": "sadness",
    "슬픔": "sadness",
    "angry": "anger",
    "anger": "anger",
    "분노": "anger",
    "fear": "anxiety",
    "anxious": "anxiety",
    "anxiety": "anxiety",
    "불안": "anxiety",
    "nervous": "anxiety",
    "neutral": "neutral",
    "중립": "neutral",
    "surprise": "surprise",
    "surprised": "surprise",
    "당황": "surprise",
    "hurt": "hurt",
    "상처": "hurt",
    "disgust": "disgust",
    "혐오": "disgust",
}

TEXT_MODEL_ID2LABEL = {
    0: "기쁨",
    1: "당황",
    2: "분노",
    3: "불안",
    4: "상처",
    5: "슬픔",
}


@dataclass(frozen=True)
class ModelProfile:
    key: str
    display_name: str
    mode: str
    audio_model_id: str
    text_model_id: str | None = None
    audio_weight: float = 1.0
    text_weight: float = 0.0


@dataclass(frozen=True)
class Prediction:
    model_key: str
    display_name: str
    mode: str
    raw_labels: dict[str, Any]
    normalized_labels: dict[str, float]
    top_label: str
    top_score: float


def build_profiles() -> dict[str, ModelProfile]:
    return {
        "wav2vec2_xlsr_ko_v1": ModelProfile(
            key="wav2vec2_xlsr_ko_v1",
            display_name="jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition",
            mode="audio",
            audio_model_id="jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition",
        ),
        "wav2vec2_xlsr_ko_v3": ModelProfile(
            key="wav2vec2_xlsr_ko_v3",
            display_name="jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3",
            mode="audio",
            audio_model_id="jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3",
        ),
        "audio_text_fusion": ModelProfile(
            key="audio_text_fusion",
            display_name="Wav2Vec2 XLSR Korean v3 + KcELECTRA Emotion Fusion",
            mode="multimodal",
            audio_model_id="jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3",
            text_model_id="noridorimari/emotion_classifier",
            audio_weight=0.65,
            text_weight=0.35,
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run Korean speech emotion PoC experiments from tests/ without touching app/."
    )
    parser.add_argument("--audio", required=True, help="Path to wav/flac audio file.")
    parser.add_argument(
        "--transcript",
        default="",
        help="Optional Korean transcript. Required for multimodal profile.",
    )
    parser.add_argument(
        "--profile",
        action="append",
        dest="profiles",
        choices=sorted(build_profiles().keys()),
        help="Profile key to run. Repeat the option to run multiple profiles.",
    )
    parser.add_argument(
        "--output",
        default=str(Path("tests") / "models" / "artifacts" / "emotion_experiment.json"),
        help="Path to save JSON output.",
    )
    return parser.parse_args()


def require_transformers() -> Any:
    try:
        from transformers import (
            AutoFeatureExtractor,
            AutoModelForAudioClassification,
            AutoModelForSequenceClassification,
            AutoTokenizer,
        )
    except ImportError as error:
        raise SystemExit(
            "transformers 가 필요합니다. `pip install transformers` 후 다시 실행하세요."
        ) from error

    return {
        "AutoFeatureExtractor": AutoFeatureExtractor,
        "AutoModelForAudioClassification": AutoModelForAudioClassification,
        "AutoModelForSequenceClassification": AutoModelForSequenceClassification,
        "AutoTokenizer": AutoTokenizer,
    }


def require_torchaudio() -> Any:
    try:
        import torchaudio
    except ImportError as error:
        raise SystemExit(
            "torchaudio 가 필요합니다. `pip install torchaudio` 후 다시 실행하세요."
        ) from error
    return torchaudio


def require_torch() -> Any:
    try:
        import torch
    except ImportError as error:
        raise SystemExit("torch 가 필요합니다. `pip install torch` 후 다시 실행하세요.") from error
    return torch


def build_wav2vec2_speech_classification_model(model_id: str) -> Any:
    torch = require_torch()
    from transformers import AutoConfig, Wav2Vec2Model, Wav2Vec2PreTrainedModel
    from transformers.modeling_outputs import SequenceClassifierOutput

    class ClassificationHead(torch.nn.Module):
        def __init__(self, config: Any) -> None:
            super().__init__()
            dropout_prob = getattr(config, "final_dropout", 0.1)
            self.dropout = torch.nn.Dropout(dropout_prob)
            self.dense = torch.nn.Linear(config.hidden_size, config.hidden_size)
            self.out_proj = torch.nn.Linear(config.hidden_size, config.num_labels)

        def forward(self, features: Any) -> Any:
            hidden = self.dropout(features)
            hidden = self.dense(hidden)
            hidden = torch.tanh(hidden)
            hidden = self.dropout(hidden)
            return self.out_proj(hidden)

    class Wav2Vec2ForSpeechClassification(Wav2Vec2PreTrainedModel):
        def __init__(self, config: Any) -> None:
            super().__init__(config)
            self.wav2vec2 = Wav2Vec2Model(config)
            self.classifier = ClassificationHead(config)
            self.post_init()

        def forward(
            self,
            input_values: Any,
            attention_mask: Any = None,
            output_attentions: bool | None = None,
            output_hidden_states: bool | None = None,
            return_dict: bool | None = None,
        ) -> Any:
            return_dict = return_dict if return_dict is not None else self.config.use_return_dict
            outputs = self.wav2vec2(
                input_values,
                attention_mask=attention_mask,
                output_attentions=output_attentions,
                output_hidden_states=output_hidden_states,
                return_dict=return_dict,
            )
            hidden_states = outputs[0]
            pooled_output = hidden_states.mean(dim=1)
            logits = self.classifier(pooled_output)

            if not return_dict:
                return (logits,) + outputs[2:]

            return SequenceClassifierOutput(
                loss=None,
                logits=logits,
                hidden_states=outputs.hidden_states,
                attentions=outputs.attentions,
            )

    config = AutoConfig.from_pretrained(model_id)
    model = Wav2Vec2ForSpeechClassification.from_pretrained(model_id, config=config)
    model.eval()
    return model


def softmax(values: np.ndarray) -> np.ndarray:
    shifted = values - np.max(values)
    exp_values = np.exp(shifted)
    return exp_values / exp_values.sum()


def canonicalize_label(label: str) -> str:
    lowered = label.strip().lower()
    if lowered in LABEL_ALIASES:
        return LABEL_ALIASES[lowered]

    for alias, canonical in LABEL_ALIASES.items():
        if alias in lowered:
            return canonical
    return "unknown"


def normalize_scores(scores: dict[str, float]) -> dict[str, float]:
    normalized: dict[str, float] = {label: 0.0 for label in COMMON_LABELS}
    for raw_label, score in scores.items():
        normalized[canonicalize_label(raw_label)] += float(score)
    return {
        label: round(value, 6)
        for label, value in sorted(normalized.items(), key=lambda item: item[1], reverse=True)
        if value > 0
    }


def load_audio(audio_path: Path, target_sr: int = 16000) -> np.ndarray:
    samples, sample_rate = sf.read(audio_path)
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    samples = samples.astype(np.float32)

    if sample_rate == target_sr:
        return samples

    torchaudio = require_torchaudio()
    torch = require_torch()
    waveform = torchaudio.functional.resample(
        waveform=torch.from_numpy(samples).unsqueeze(0),
        orig_freq=sample_rate,
        new_freq=target_sr,
    )
    return waveform.squeeze(0).numpy()


def run_audio_model(
    audio_model_id: str, audio_path: Path
) -> tuple[dict[str, float], dict[str, float]]:
    transformers = require_transformers()
    feature_extractor = transformers["AutoFeatureExtractor"].from_pretrained(audio_model_id)
    from transformers import AutoConfig

    config = AutoConfig.from_pretrained(audio_model_id)
    if getattr(config, "architectures", []) == ["Wav2Vec2ForSpeechClassification"]:
        model = build_wav2vec2_speech_classification_model(audio_model_id)
    else:
        model = transformers["AutoModelForAudioClassification"].from_pretrained(audio_model_id)

    samples = load_audio(audio_path, target_sr=feature_extractor.sampling_rate)
    inputs = feature_extractor(
        samples,
        sampling_rate=feature_extractor.sampling_rate,
        return_tensors="pt",
    )
    logits = model(**inputs).logits.float().detach().cpu().numpy()[0]
    probabilities = softmax(logits)
    raw_scores = {
        model.config.id2label[index]: float(probability)
        for index, probability in enumerate(probabilities)
    }
    return raw_scores, normalize_scores(raw_scores)


def run_text_model(
    text_model_id: str, transcript: str
) -> tuple[dict[str, float], dict[str, float]]:
    if not transcript.strip():
        raise SystemExit("멀티모달 프로필은 `--transcript` 값이 필요합니다.")

    transformers = require_transformers()
    tokenizer = transformers["AutoTokenizer"].from_pretrained(text_model_id)
    model = transformers["AutoModelForSequenceClassification"].from_pretrained(text_model_id)
    model.config.id2label = TEXT_MODEL_ID2LABEL
    inputs = tokenizer(transcript, return_tensors="pt", truncation=True)
    logits = model(**inputs).logits.float().detach().cpu().numpy()[0]
    probabilities = softmax(logits)
    raw_scores = {
        model.config.id2label[index]: float(probability)
        for index, probability in enumerate(probabilities)
    }
    return raw_scores, normalize_scores(raw_scores)


def fuse_scores(
    audio_scores: dict[str, float],
    text_scores: dict[str, float],
    *,
    audio_weight: float,
    text_weight: float,
) -> dict[str, float]:
    merged: dict[str, float] = {label: 0.0 for label in COMMON_LABELS}
    for label, score in audio_scores.items():
        merged[label] += score * audio_weight
    for label, score in text_scores.items():
        merged[label] += score * text_weight
    return {
        label: round(value, 6)
        for label, value in sorted(merged.items(), key=lambda item: item[1], reverse=True)
        if value > 0
    }


def to_prediction(
    profile: ModelProfile, raw_labels: dict[str, float], normalized: dict[str, float]
) -> Prediction:
    top_label, top_score = next(iter(normalized.items()))
    return Prediction(
        model_key=profile.key,
        display_name=profile.display_name,
        mode=profile.mode,
        raw_labels={key: round(value, 6) for key, value in raw_labels.items()},
        normalized_labels=normalized,
        top_label=top_label,
        top_score=round(top_score, 6),
    )


def run_profile(profile: ModelProfile, audio_path: Path, transcript: str) -> Prediction:
    if profile.mode == "audio":
        raw_labels, normalized = run_audio_model(profile.audio_model_id, audio_path)
        return to_prediction(profile, raw_labels, normalized)

    audio_raw, audio_normalized = run_audio_model(profile.audio_model_id, audio_path)
    text_raw, text_normalized = run_text_model(profile.text_model_id or "", transcript)
    fused_normalized = fuse_scores(
        audio_normalized,
        text_normalized,
        audio_weight=profile.audio_weight,
        text_weight=profile.text_weight,
    )
    raw_labels = {
        "audio": audio_raw,
        "text": text_raw,
    }
    top_label, top_score = next(iter(fused_normalized.items()))
    return Prediction(
        model_key=profile.key,
        display_name=profile.display_name,
        mode=profile.mode,
        raw_labels=raw_labels,
        normalized_labels=fused_normalized,
        top_label=top_label,
        top_score=round(top_score, 6),
    )


def main() -> None:
    args = parse_args()
    audio_path = Path(args.audio)
    if not audio_path.exists():
        raise SystemExit(f"audio file not found: {audio_path}")

    profiles = build_profiles()
    selected_profiles = args.profiles or list(profiles.keys())
    results = [run_profile(profiles[key], audio_path, args.transcript) for key in selected_profiles]

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps([asdict(result) for result in results], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    for result in results:
        print(f"[{result.model_key}] {result.top_label} ({result.top_score:.4f})")
        print(json.dumps(result.normalized_labels, ensure_ascii=False))

    print(f"saved: {output_path}")


if __name__ == "__main__":
    main()

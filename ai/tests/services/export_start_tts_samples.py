from __future__ import annotations

import argparse
import base64
from pathlib import Path

from app.models.tts.voice_catalog import SUPERTONIC_VOICE_NAME_TO_ID
from app.services.interview_service import generate_interview_start_tts

DEFAULT_SAMPLE_TEXT = "안녕하세요. 만나서 반갑습니다!"
DEFAULT_OUTPUT_DIR = Path("tests") / "services" / "artifacts" / "start_tts_samples"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate start-tts sample WAV files for the selectable voices."
    )
    parser.add_argument(
        "--voices",
        nargs="*",
        default=list(SUPERTONIC_VOICE_NAME_TO_ID.keys()),
        help="Voice display names to export. Default: all selectable voices.",
    )
    parser.add_argument(
        "--text",
        default=DEFAULT_SAMPLE_TEXT,
        help="Fixed sample text to synthesize.",
    )
    parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_OUTPUT_DIR),
        help="Directory where WAV files will be saved.",
    )
    return parser.parse_args()


def export_voice_sample(*, voice: str, sample_text: str, output_dir: Path) -> Path:
    result = generate_interview_start_tts(
        is_first_finished=True,
        db_question=sample_text,
        voice=voice,
    )
    output_path = output_dir / f"{voice.lower()}.wav"
    output_path.write_bytes(base64.b64decode(result["tts_audio_base64"]))
    return output_path


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"sample_text={args.text}")
    print(f"output_dir={output_dir}")

    for voice in args.voices:
        if voice not in SUPERTONIC_VOICE_NAME_TO_ID:
            supported = ", ".join(SUPERTONIC_VOICE_NAME_TO_ID)
            raise SystemExit(f"Unsupported voice: {voice}. Supported values: {supported}")

        output_path = export_voice_sample(
            voice=voice,
            sample_text=args.text,
            output_dir=output_dir,
        )
        print(f"generated={voice} -> {output_path}")


if __name__ == "__main__":
    main()

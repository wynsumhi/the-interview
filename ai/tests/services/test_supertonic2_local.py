import argparse
import statistics
import time
from pathlib import Path
from typing import Any

DEFAULT_REPEAT = 3
DEFAULT_LANG = "ko"
DEFAULT_VOICE = "F3"
DEFAULT_STEPS = 5
DEFAULT_SPEED = 1.05

SHORT_TEXT = "안녕하세요. 수퍼토닉2 로컬 TTS 속도 테스트를 시작합니다."
MEDIUM_TEXT = (
    "안녕하세요. 이 문장은 수퍼토닉2 로컬 모델의 기본 음성 합성 속도와 "
    "품질을 함께 확인하기 위한 중간 길이 테스트 문장입니다. "
    "실제 면접 질문 안내처럼 자연스럽게 읽히는지도 같이 확인합니다."
)
LONG_TEXT = (
    "안녕하세요. 이 문장은 수퍼토닉2 로컬 모델이 긴 텍스트를 얼마나 빠르게 "
    "처리하는지 확인하기 위한 테스트입니다. "
    "면접 서비스에서는 사용자가 질문을 기다리는 시간이 길어지면 체감이 크게 "
    "나빠질 수 있기 때문에, "
    "짧은 문장뿐 아니라 실제 운영에서 나올 수 있는 긴 질문 길이에서도 생성 "
    "시간이 안정적으로 유지되는지 살펴보는 것이 중요합니다. "
    "이번 실험에서는 동일한 설정으로 여러 번 반복 실행해 평균 지연 시간과 "
    "최소, 최대 지연 시간을 함께 확인합니다."
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run local Supertonic 2 TTS benchmark under tests/ without touching app/."
    )
    parser.add_argument("--voice", default=DEFAULT_VOICE, help="Voice name such as M1, F1, F3.")
    parser.add_argument("--lang", default=DEFAULT_LANG, help="Language code: en, ko, es, pt, fr.")
    parser.add_argument(
        "--steps", type=int, default=DEFAULT_STEPS, help="Quality steps. Default: 5"
    )
    parser.add_argument(
        "--speed", type=float, default=DEFAULT_SPEED, help="Speech speed. Default: 1.05"
    )
    parser.add_argument(
        "--repeat", type=int, default=DEFAULT_REPEAT, help="Warm benchmark repeat count."
    )
    parser.add_argument(
        "--provider",
        default="cpu",
        choices=["cpu", "cuda", "auto"],
        help="ONNX Runtime provider to use.",
    )
    parser.add_argument(
        "--output-dir",
        default=str(Path("tests") / "services" / "artifacts" / "supertonic2"),
        help="Directory to save generated wav files.",
    )
    parser.add_argument(
        "--skip-save",
        action="store_true",
        help="Do not save output wav files. Latency benchmark still runs.",
    )
    parser.add_argument(
        "--auto-download",
        action="store_true",
        help="Allow first-run automatic model download. Recommended on first execution.",
    )
    return parser.parse_args()


def require_supertonic() -> Any:
    try:
        from supertonic import TTS
    except ImportError as error:
        raise SystemExit(
            "supertonic 패키지가 설치되어 있지 않습니다.\n"
            "먼저 `pip install supertonic` 를 실행한 뒤 다시 시도하세요.\n"
            "첫 실행에서는 모델이 자동 다운로드되며 약 300MB 정도가 필요합니다."
        ) from error
    return TTS


def configure_provider(provider: str) -> None:
    import onnxruntime as ort
    import supertonic.loader as loader

    if provider == "cpu":
        loader.DEFAULT_ONNX_PROVIDERS = ["CPUExecutionProvider"]
    elif provider == "cuda":
        ort.preload_dlls()
        loader.DEFAULT_ONNX_PROVIDERS = ["CUDAExecutionProvider", "CPUExecutionProvider"]
    else:
        ort.preload_dlls()
        loader.DEFAULT_ONNX_PROVIDERS = [
            "TensorrtExecutionProvider",
            "CUDAExecutionProvider",
            "CPUExecutionProvider",
        ]


def normalize_duration(duration: Any) -> float:
    if isinstance(duration, int | float):
        return float(duration)
    if hasattr(duration, "tolist"):
        value = duration.tolist()
        if isinstance(value, list):
            return float(value[0])
        return float(value)
    if isinstance(duration, list | tuple):
        return float(duration[0])
    raise TypeError(f"Unsupported duration type: {type(duration)!r}")


def synthesize_once(
    tts: Any,
    *,
    text: str,
    voice: str,
    lang: str,
    steps: int,
    speed: float,
    output_path: Path | None,
) -> tuple[float, float]:
    voice_style = tts.get_voice_style(voice_name=voice)

    start = time.perf_counter()
    wav, duration = tts.synthesize(
        text,
        voice_style=voice_style,
        lang=lang,
        total_steps=steps,
        speed=speed,
    )
    latency = time.perf_counter() - start
    audio_duration = normalize_duration(duration)

    if output_path is not None:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        tts.save_audio(wav, str(output_path))

    return latency, audio_duration


def print_summary(label: str, latencies: list[float], audio_duration: float, text: str) -> None:
    avg = statistics.mean(latencies)
    std = statistics.stdev(latencies) if len(latencies) > 1 else 0.0
    chars_per_sec = len(text) / avg if avg > 0 else 0.0
    rtf = avg / audio_duration if audio_duration > 0 else 0.0

    print(f"\n{'=' * 70}")
    print(f"[TEST CASE] {label}")
    print(f"text_length={len(text)}")
    print(f"text={text}")
    print(f"average_latency={avg:.3f}s")
    print(f"std_latency={std:.3f}s")
    print(f"min_latency={min(latencies):.3f}s")
    print(f"max_latency={max(latencies):.3f}s")
    print(f"audio_duration={audio_duration:.3f}s")
    print(f"chars_per_sec={chars_per_sec:.2f}")
    print(f"real_time_factor={rtf:.3f}")
    print(f"{'=' * 70}")


def run_case(
    tts: Any,
    *,
    label: str,
    text: str,
    voice: str,
    lang: str,
    steps: int,
    speed: float,
    repeat: int,
    output_dir: Path,
    skip_save: bool,
) -> None:
    latencies: list[float] = []
    final_audio_duration = 0.0

    print(f"\n--- {label} ---")
    for index in range(repeat):
        output_path = None
        if not skip_save and index == repeat - 1:
            output_path = output_dir / f"{label}.wav"

        latency, audio_duration = synthesize_once(
            tts,
            text=text,
            voice=voice,
            lang=lang,
            steps=steps,
            speed=speed,
            output_path=output_path,
        )
        latencies.append(latency)
        final_audio_duration = audio_duration
        print(f"run {index + 1:>2}: {latency:.3f}s")

    print_summary(label, latencies, final_audio_duration, text)


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    TTS = require_supertonic()

    print("########## SUPERTONIC2 LOCAL TTS BENCHMARK ##########")
    print(f"voice={args.voice}")
    print(f"lang={args.lang}")
    print(f"steps={args.steps}")
    print(f"speed={args.speed}")
    print(f"repeat={args.repeat}")
    print(f"auto_download={args.auto_download}")
    print(f"provider={args.provider}")

    configure_provider(args.provider)
    init_start = time.perf_counter()
    tts = TTS(auto_download=args.auto_download)
    init_elapsed = time.perf_counter() - init_start
    print(f"init_latency={init_elapsed:.3f}s")
    print(f"session_providers={tts.model.dp_ort.get_providers()}")

    available_voices = getattr(tts, "voice_style_names", None)
    if available_voices:
        print(f"available_voices={', '.join(available_voices)}")
        if args.voice not in available_voices:
            raise SystemExit(f"지원되지 않는 voice 입니다: {args.voice}")

    run_case(
        tts,
        label="short",
        text=SHORT_TEXT,
        voice=args.voice,
        lang=args.lang,
        steps=args.steps,
        speed=args.speed,
        repeat=args.repeat,
        output_dir=output_dir,
        skip_save=args.skip_save,
    )
    run_case(
        tts,
        label="medium",
        text=MEDIUM_TEXT,
        voice=args.voice,
        lang=args.lang,
        steps=args.steps,
        speed=args.speed,
        repeat=args.repeat,
        output_dir=output_dir,
        skip_save=args.skip_save,
    )
    run_case(
        tts,
        label="long",
        text=LONG_TEXT,
        voice=args.voice,
        lang=args.lang,
        steps=args.steps,
        speed=args.speed,
        repeat=args.repeat,
        output_dir=output_dir,
        skip_save=args.skip_save,
    )


if __name__ == "__main__":
    main()

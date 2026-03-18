import argparse
import statistics
import time
from pathlib import Path
from typing import Any

from supertonic import TTS
from supertonic.utils import chunk_text

DEFAULT_LANG = "ko"
DEFAULT_VOICE = "F3"
DEFAULT_STEPS = 5
DEFAULT_SPEED = 1.05
DEFAULT_REPEAT = 3
DEFAULT_MAX_CHUNK_LENGTH = 120

LONG_TEXT = (
    "안녕하세요. 이 문장은 웹 데모에서 느껴지는 빠른 재생 시작 속도와 "
    "로컬에서 측정하는 전체 음성 생성 완료 시간을 비교하기 위한 테스트입니다. "
    "실제 서비스에서는 사용자가 첫 음성을 빨리 듣기 시작하는 것이 중요할 수 있고, "
    "동시에 전체 질문 음성이 모두 준비되기까지 걸리는 시간도 중요합니다. "
    "따라서 이번 비교에서는 긴 문장을 여러 청크로 나누었을 때 첫 번째 청크가 "
    "준비되는 시간과 전체 청크가 모두 생성되는 시간을 함께 측정합니다."
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare full synthesis latency vs first-audio latency."
    )
    parser.add_argument("--voice", default=DEFAULT_VOICE)
    parser.add_argument("--lang", default=DEFAULT_LANG)
    parser.add_argument("--steps", type=int, default=DEFAULT_STEPS)
    parser.add_argument("--speed", type=float, default=DEFAULT_SPEED)
    parser.add_argument("--repeat", type=int, default=DEFAULT_REPEAT)
    parser.add_argument("--max-chunk-length", type=int, default=DEFAULT_MAX_CHUNK_LENGTH)
    parser.add_argument("--provider", default="cpu", choices=["cpu", "cuda", "auto"])
    parser.add_argument(
        "--output-dir",
        default=str(Path("tests") / "services" / "artifacts" / "supertonic2_streaming"),
    )
    return parser.parse_args()


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


def run_full_call(
    tts: TTS,
    *,
    text: str,
    voice_style: Any,
    lang: str,
    steps: int,
    speed: float,
    max_chunk_length: int,
) -> tuple[float, float, Any]:
    start = time.perf_counter()
    wav, duration = tts.synthesize(
        text,
        voice_style=voice_style,
        lang=lang,
        total_steps=steps,
        speed=speed,
        max_chunk_length=max_chunk_length,
    )
    latency = time.perf_counter() - start
    return latency, normalize_duration(duration), wav


def run_chunked_call(
    tts: TTS,
    *,
    chunks: list[str],
    voice_style: Any,
    lang: str,
    steps: int,
    speed: float,
) -> tuple[float, float, float]:
    first_chunk_latency = 0.0
    total_latency = 0.0
    total_audio_duration = 0.0

    for index, chunk in enumerate(chunks):
        start = time.perf_counter()
        _, duration = tts.synthesize(
            chunk,
            voice_style=voice_style,
            lang=lang,
            total_steps=steps,
            speed=speed,
            max_chunk_length=len(chunk),
            silence_duration=0.0,
        )
        latency = time.perf_counter() - start
        total_latency += latency
        total_audio_duration += normalize_duration(duration)
        if index == 0:
            first_chunk_latency = latency

    return first_chunk_latency, total_latency, total_audio_duration


def print_stat(label: str, values: list[float]) -> None:
    avg = statistics.mean(values)
    std = statistics.stdev(values) if len(values) > 1 else 0.0
    print(f"{label}_avg={avg:.3f}s")
    print(f"{label}_std={std:.3f}s")
    print(f"{label}_min={min(values):.3f}s")
    print(f"{label}_max={max(values):.3f}s")


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print("########## SUPERTONIC2 STREAMING-LIKE COMPARISON ##########")
    print(f"voice={args.voice}")
    print(f"lang={args.lang}")
    print(f"steps={args.steps}")
    print(f"speed={args.speed}")
    print(f"repeat={args.repeat}")
    print(f"max_chunk_length={args.max_chunk_length}")
    print(f"provider={args.provider}")
    print(f"text_length={len(LONG_TEXT)}")

    configure_provider(args.provider)
    tts = TTS(auto_download=False)
    print(f"session_providers={tts.model.dp_ort.get_providers()}")
    voice_style = tts.get_voice_style(args.voice)
    chunks = chunk_text(LONG_TEXT, args.max_chunk_length)

    print(f"chunk_count={len(chunks)}")
    for index, chunk in enumerate(chunks, start=1):
        print(f"chunk_{index}_length={len(chunk)}")
        print(f"chunk_{index}={chunk}")

    full_latencies: list[float] = []
    full_audio_durations: list[float] = []
    first_chunk_latencies: list[float] = []
    chunked_total_latencies: list[float] = []
    chunked_audio_durations: list[float] = []

    for run_index in range(args.repeat):
        print(f"\n--- run {run_index + 1} ---")

        full_latency, full_audio_duration, wav = run_full_call(
            tts,
            text=LONG_TEXT,
            voice_style=voice_style,
            lang=args.lang,
            steps=args.steps,
            speed=args.speed,
            max_chunk_length=args.max_chunk_length,
        )
        full_latencies.append(full_latency)
        full_audio_durations.append(full_audio_duration)
        print(f"full_call_total={full_latency:.3f}s")

        first_chunk_latency, chunked_total_latency, chunked_audio_duration = run_chunked_call(
            tts,
            chunks=chunks,
            voice_style=voice_style,
            lang=args.lang,
            steps=args.steps,
            speed=args.speed,
        )
        first_chunk_latencies.append(first_chunk_latency)
        chunked_total_latencies.append(chunked_total_latency)
        chunked_audio_durations.append(chunked_audio_duration)
        print(f"first_chunk_latency={first_chunk_latency:.3f}s")
        print(f"chunked_total_latency={chunked_total_latency:.3f}s")

        if run_index == args.repeat - 1:
            tts.save_audio(wav, str(output_dir / "full_call.wav"))

    print(f"\n{'=' * 70}")
    print("[SUMMARY]")
    print_stat("full_call_total", full_latencies)
    print_stat("first_chunk", first_chunk_latencies)
    print_stat("chunked_total", chunked_total_latencies)
    print(f"full_audio_duration_avg={statistics.mean(full_audio_durations):.3f}s")
    print(f"chunked_audio_duration_avg={statistics.mean(chunked_audio_durations):.3f}s")
    first_chunk_vs_full_ratio = statistics.mean(first_chunk_latencies) / statistics.mean(
        full_latencies
    )
    first_chunk_vs_audio_ratio = statistics.mean(first_chunk_latencies) / statistics.mean(
        full_audio_durations
    )

    print(f"first_chunk_vs_full_ratio={first_chunk_vs_full_ratio:.3f}")
    print(f"first_chunk_vs_audio_ratio={first_chunk_vs_audio_ratio:.3f}")
    print(f"{'=' * 70}")


if __name__ == "__main__":
    main()

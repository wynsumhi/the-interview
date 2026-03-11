import os
import re
import statistics
import sys
import time

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.tts_service import text_to_speech


def split_sentences(text: str):
    """
    문장을 ?, !, . 기준으로 분리
    """
    sentences = re.split(r"(?<=[?.!])\s+", text.strip())
    return [s for s in sentences if s]


def run_split_latency_test(label: str, text: str, repeat: int = 5):
    """
    TTS Split 요청 시 문장별 latency 측정
    """

    sentences = split_sentences(text)

    print("\n" + "=" * 70)
    print(f"[TEST CASE] {label}")

    print("\n[전체 텍스트]")
    print(text)

    print("\n[문장 분리]")
    for i, s in enumerate(sentences, 1):
        print(f"{i}. {s}")

    # ------------------------------------------------
    # Full Request
    # ------------------------------------------------
    print("\n--- Full Request Latency ---")

    full_times = []

    for i in range(repeat):
        start = time.time()
        text_to_speech(text)
        elapsed = time.time() - start

        full_times.append(elapsed)

        print(f"Run {i + 1:>2}: {elapsed:.3f}s")

    full_avg = statistics.mean(full_times)

    # ------------------------------------------------
    # Split Sentence Latency
    # ------------------------------------------------
    print("\n--- Split Sentence Latency ---")

    sentence_times = [[] for _ in sentences]

    for r in range(repeat):
        print(f"\nRun {r + 1}")

        for i, sentence in enumerate(sentences):
            start = time.time()
            text_to_speech(sentence)
            elapsed = time.time() - start

            sentence_times[i].append(elapsed)

            print(f"Sentence {i + 1}: {elapsed:.3f}s")

    # ------------------------------------------------
    # Summary
    # ------------------------------------------------
    print("\n--- Summary ---")

    print(f"Full Request Avg: {full_avg:.3f}s\n")

    for i, times in enumerate(sentence_times):
        avg = statistics.mean(times)
        std = statistics.stdev(times) if len(times) > 1 else 0

        print(f"Sentence {i + 1} Avg: {avg:.3f}s (std {std:.3f})")

    print("=" * 70)


# -------------------------------------------------------
# TEST CASE
# -------------------------------------------------------

TEST_CASE_1 = (
    "오늘 하루를 한 단어로 표현한다면 어떤 단어가 떠오르나요? 그 이유도 함께 말씀해 주세요."
)

TEST_CASE_2 = (
    "지금까지 살아오면서 기억에 남는 사람이 있나요? "
    "그 사람은 어떤 사람이었나요? "
    "그 사람에게서 무엇을 배우셨나요?"
)

TEST_CASE_3 = (
    "최근 일상에서 문득 행복하다고 느꼈던 순간이 있으셨나요? "
    "그 순간은 어떤 상황이었나요? "
    "왜 그때 그런 감정이 들었다고 생각하시나요?"
)


if __name__ == "__main__":
    print("\n########## SPLIT TTS LATENCY TEST ##########")

    run_split_latency_test("Case 1", TEST_CASE_1)
    run_split_latency_test("Case 2", TEST_CASE_2)
    run_split_latency_test("Case 3", TEST_CASE_3)

import os
import statistics
import sys
import time
from io import BytesIO

import tiktoken
from mutagen.mp3 import MP3

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.tts_service import text_to_speech

encoding = tiktoken.get_encoding("cl100k_base")


def count_tokens(text: str) -> int:
    """
    텍스트를 토큰 단위로 계산하는 함수

    TTS는 토큰 단위로 처리하므로,
    실제 발화 길이를 예측하기 위해 글자 수 대신 토큰 수로 측정
    """
    return len(encoding.encode(text))


def get_audio_duration(audio_bytes):
    """
    mp3 음성 파일의 길이를 측정하는 함수 (단위: 초)

    TTS로 생성된 mp3 음성의 실제 재생 길이를 측정하기 위함

    TTS 응답 시간만 측정하면 음성이 얼마나 길게 재생되는지 알 수 없기 때문에,
    질문 재생 시간 UX를 판단하기 위해 사용
    """
    audio = MP3(BytesIO(audio_bytes))
    return audio.info.length


def run_tts_benchmark(label: str, text: str, repeat: int = 10):
    """
    TTS 성능 테스트

    측정 항목
    - Warm Request 평균 / 표준편차 / 최솟값 / 최댓값
    - 음성 길이
    - Speech rate (tokens/sec)
    """

    token_count = count_tokens(text)

    print(f"\n{'=' * 70}")
    print(f"[TEST CASE] {label}")
    print(f"[글자 수] {len(text)}자")
    print(f"[토큰 수] {token_count}")
    print(f"[텍스트] {text}")

    # ------------------------------
    # Warm Requests
    # ------------------------------
    print("\n--- Warm Requests ---")

    times = []
    audio_bytes = None

    for i in range(repeat):
        start = time.time()
        audio_bytes = text_to_speech(text)
        elapsed = time.time() - start

        times.append(elapsed)

        print(f"Run {i + 1:>2}: {elapsed:.3f}s")

    avg = statistics.mean(times)
    std = statistics.stdev(times) if len(times) > 1 else 0

    duration = get_audio_duration(audio_bytes)
    tokens_per_sec = token_count / duration if duration > 0 else 0

    print("\n--- Summary ---")
    print(f"Average latency : {avg:.3f}s")
    print(f"Std deviation   : {std:.3f}s")
    print(f"Min latency     : {min(times):.3f}s")
    print(f"Max latency     : {max(times):.3f}s")
    print(f"Audio duration  : {duration:.2f}s")
    print(f"Speech rate     : {tokens_per_sec:.2f} tokens/sec")

    print(f"{'=' * 70}")


# -------------------------------------------------------
# 글자 수 테스트
# -------------------------------------------------------

# 약 20자
TEST_CASE_1 = "요즘 어떤 생각을 가장 많이 하고 계신가요?"

# 약 50자
TEST_CASE_2 = (
    "오늘 하루를 한 단어로 표현한다면 어떤 단어가 떠오르나요? 그 이유도 함께 말씀해 주세요."
)

# 약 100자
TEST_CASE_3 = (
    "안녕하세요. 오늘 인터뷰에 참여해 주셔서 진심으로 감사합니다. "
    "너무 부담 갖지 마시고 편안하게 답변해 주시면 됩니다. "
    "그럼 첫 번째 질문입니다. 요즘 가장 많이 생각하는 것이 무엇인가요?"
)

# 약 150자
TEST_CASE_4 = (
    "우리는 살면서 수많은 순간을 경험하고, 그 안에서 조금씩 성장해 나가죠. "
    "그 중에서도 유독 기억에 남는 순간이 있을 것 같아요. "
    "지금까지 살아오면서 스스로 가장 많이 성장했다고 느꼈던 순간은 언제였나요? "
    "그 순간이 어떤 상황이었는지, 어떤 감정이 들었는지 편하게 이야기해 주세요."
)

# 약 200자
TEST_CASE_5 = (
    "사람들은 저마다 행복을 느끼는 순간이 다르다고 하잖아요. "
    "어떤 사람은 혼자만의 고요한 시간에서 행복을 느끼고, "
    "어떤 사람은 소중한 사람들과 함께할 때 가장 충만함을 느끼기도 하죠. "
    "최근 일상에서 문득 행복하다고 느꼈던 순간이 있으셨나요? "
    "그 순간이 어떤 상황이었는지, 왜 그때 그런 따뜻한 감정이 들었는지"
    "떠오르는 대로 편하게 이야기해 주시면 좋겠습니다."
)

# 약 250자
TEST_CASE_6 = (
    "살다 보면 내가 선택한 것인지, 아니면 주변 상황이나 사람들의 기대에 의해 "
    "자연스럽게 흘러온 것인지 구분이 어려울 때가 있잖아요. "
    "지금까지의 삶을 돌아봤을 때, 스스로 능동적으로 선택했다고 느끼는 순간이 있으셨나요? "
    "그러한 순간이 있다면, 그 선택이 지금의 나에게 어떤 영향을 주었다고 생각하시는지 들려주세요. "
    "거창한 선택이 아니어도 괜찮습니다. 아주 작고 사소한 선택이라도 좋으니 "
    "부담 없이 천천히 떠오르는 대로 편하게 말씀해 주시면 됩니다."
)


# -------------------------------------------------------
# 문장 수 (토큰) 테스트
# -------------------------------------------------------

TEST_SENTENCE_1 = (
    "지금까지 살아오면서 나에게 가장 큰 영향을 준 사람이 있다면 누구인지, "
    "그리고 그 사람이 나의 생각이나 삶의 방향에 어떤 변화를 가져왔는지 "
    "편하게 이야기해 주실 수 있을까요?"
)

TEST_SENTENCE_2 = (
    "지금까지 살아오면서 기억에 남는 사람이 있나요? "
    "그 사람은 어떤 사람이었나요? "
    "그 사람과의 관계에서 무엇을 배우셨나요? "
    "그 경험이 지금의 나에게 어떤 영향을 주었다고 생각하시나요? "
    "편하게 떠오르는 대로 이야기해 주세요."
)


if __name__ == "__main__":
    print("\n\n########## LENGTH TEST ##########")

    run_tts_benchmark("Length 20", TEST_CASE_1)
    run_tts_benchmark("Length 50", TEST_CASE_2)
    run_tts_benchmark("Length 100", TEST_CASE_3)
    run_tts_benchmark("Length 150", TEST_CASE_4)
    run_tts_benchmark("Length 200", TEST_CASE_5)
    run_tts_benchmark("Length 250", TEST_CASE_6)

    print("\n\n########## SENTENCE COUNT TEST ##########")

    run_tts_benchmark("Sentence 1", TEST_SENTENCE_1)
    run_tts_benchmark("Sentence 3", TEST_SENTENCE_2)

# 한국어 감정 분석 PoC 실험

이 디렉터리는 `ai/app`을 건드리지 않고 한국어 음성 감정 분석 후보를 비교하기 위한 실험 코드만 둔다.

## 실험 대상

1. `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition`
2. `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3`
3. `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3` + `noridorimari/emotion_classifier` fusion

세 번째는 공개된 한국어 end-to-end 멀티모달 감정 체크포인트가 부족한 현실을 감안한 PoC용 대체안이다. 음성 점수와 한국어 텍스트 감정 점수를 공통 라벨 공간으로 정규화한 뒤 가중합한다.

## 비교표

| 프로필 | 음성 모델 | 텍스트 모델 | 분류 감정 | 중립 포함 여부 |
| --- | --- | --- | --- | --- |
| `wav2vec2_xlsr_ko_v1` | `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition` | 없음 | `기쁨`, `당황`, `분노`, `불안`, `슬픔` | 없음 |
| `wav2vec2_xlsr_ko_v3` | `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3` | 없음 | `기쁨`, `당황`, `분노`, `불안`, `슬픔`, `중립` | 있음 |
| `audio_text_fusion` | `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3` | `noridorimari/emotion_classifier` | 공통 라벨 기준 `joy`, `sadness`, `anger`, `anxiety`, `neutral`, `surprise`, `hurt` | 부분 포함 |

`부분 포함`인 이유:
- 음성 모델은 `중립`을 분류한다.
- 텍스트 모델은 `기쁨`, `당황`, `분노`, `불안`, `상처`, `슬픔` 6개만 분류하고 `중립`은 없다.
- 따라서 fusion의 `neutral`은 음성 모델 쪽 점수에만 의존한다.

## 실행 예시

```bash
python tests/models/emotion_experiment.py ^
  --audio path\\to\\sample.wav ^
  --transcript "저는 오늘 면접이 조금 긴장되지만 잘 해보고 싶습니다." ^
  --profile wav2vec2_xlsr_ko_v1 ^
  --profile wav2vec2_xlsr_ko_v3 ^
  --profile audio_text_fusion
```

결과는 기본값으로 `tests/models/artifacts/emotion_experiment.json`에 저장된다.

## 참고

- 멀티모달 profile은 `--transcript`가 반드시 필요하다.
- 각 모델의 원래 라벨 체계가 다를 수 있어서, 실험 단계에서는 `joy`, `sadness`, `anger`, `anxiety`, `neutral`, `surprise`, `hurt`, `disgust` 중심의 공통 라벨로 맵핑한다.
- 실제 비교를 공정하게 하려면 동일 음성 샘플과 수동 정답 라벨 셋을 먼저 만들어야 한다.

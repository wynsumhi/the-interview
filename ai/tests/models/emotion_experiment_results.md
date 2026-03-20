# 한국어 음성 감정 분석 3종 실험 결과

작성일: 2026-03-19

## 실험 목적

`ai/app`은 수정하지 않고 `ai/tests`에서 먼저 한국어 음성 감정 분석 후보 3종을 비교한다.

이번 정리는 특히 `중립` 감정 포함 여부를 명확히 구분하는 데 초점을 둔다.

## 실험 대상

| 프로필 | 구성 | 분류 감정 | 중립 포함 여부 |
| --- | --- | --- | --- |
| `wav2vec2_xlsr_ko_v1` | `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition` | `기쁨`, `당황`, `분노`, `불안`, `슬픔` | 없음 |
| `wav2vec2_xlsr_ko_v3` | `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3` | `기쁨`, `당황`, `분노`, `불안`, `슬픔`, `중립` | 있음 |
| `audio_text_fusion` | `wav2vec2_xlsr_ko_v3` + `noridorimari/emotion_classifier` | 공통 라벨 기준 `joy`, `sadness`, `anger`, `anxiety`, `neutral`, `surprise`, `hurt` | 부분 포함 |

`audio_text_fusion`이 `부분 포함`인 이유:
- 음성 모델은 `중립`을 예측할 수 있다.
- 텍스트 모델은 `기쁨`, `당황`, `분노`, `불안`, `상처`, `슬픔`만 예측하고 `중립`은 없다.
- 따라서 fusion 결과의 `neutral`은 음성 모델 점수에만 의존한다.

## 실험 입력

- 음성 파일: [37f21b4a-77af-473d-8178-92162568d776.webm](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/data/37f21b4a-77af-473d-8178-92162568d776.webm)
- 변환 파일: [37f21b4a-77af-473d-8178-92162568d776.wav](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/data/37f21b4a-77af-473d-8178-92162568d776.wav)
- 전사문: `프로젝트를 진행하며 바쁘게 지내고 있어요`

## 실행 결과

### 1. neutral 성향으로 판단한 샘플

입력:
- 음성 파일: [37f21b4a-77af-473d-8178-92162568d776.webm](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/data/37f21b4a-77af-473d-8178-92162568d776.webm)
- 전사문: `프로젝트를 진행하며 바쁘게 지내고 있어요`
- 사용자 기대 라벨: `중립`

| 프로필 | top 예측 | score | 상위 분포 요약 |
| --- | --- | --- | --- |
| `wav2vec2_xlsr_ko_v1` | `anger` | `0.998269` | `anger 0.998269`, `surprise 0.000997`, `anxiety 0.000448` |
| `wav2vec2_xlsr_ko_v3` | `anger` | `0.999234` | `anger 0.999234`, `sadness 0.000547`, `joy 0.000209` |
| `audio_text_fusion` | `anger` | `0.652377` | `anger 0.652377`, `joy 0.326060`, `anxiety 0.012988` |

해석:
- 세 모델 모두 사용자 기대와 다르게 `anger` 쪽으로 치우쳤다.
- 이 샘플 기준으로는 현재 후보들이 `중립` 판별에 신뢰하기 어렵다.

### 2. sad_sample

입력:
- 음성 파일: [sad_sample.wav](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/data/sad_sample.wav)
- 전사문: `그냥 모든 행동이 신경 쓰이는 사람이 있어요. 내가 하는 행동이든, 그 사람이 하는 행동이든.`
- 파일명 기준 기대 라벨: `슬픔` 또는 `우울/불안 계열`

| 프로필 | top 예측 | score | 상위 분포 요약 |
| --- | --- | --- | --- |
| `wav2vec2_xlsr_ko_v1` | `anger` | `0.900685` | `anger 0.900685`, `surprise 0.081086`, `sadness 0.012774` |
| `wav2vec2_xlsr_ko_v3` | `surprise` | `0.999769` | `surprise 0.999769`, `anxiety 0.000200`, `anger 0.000015` |
| `audio_text_fusion` | `surprise` | `0.676080` | `surprise 0.676080`, `anxiety 0.204840`, `anger 0.093003` |

해석:
- `sad_sample`에서도 세 모델 모두 `sadness`를 top으로 주지 않았다.
- `v1`은 `anger`, `v3`와 `fusion`은 `surprise` 쪽으로 쏠렸다.
- 이 결과는 현재 후보들이 저강도 부정 정서를 `슬픔`보다 `분노/당황` 쪽으로 오해할 가능성을 시사한다.

## 중립 감정 관점 해석

### 1. `wav2vec2_xlsr_ko_v1`

- 애초에 `중립` 라벨이 없다.
- 따라서 차분한 발화라도 다섯 감정 중 하나로 강제로 분류된다.
- 인터뷰 답변처럼 감정 강도가 낮은 음성에는 구조적으로 불리하다.

### 2. `wav2vec2_xlsr_ko_v3`

- `중립` 라벨이 존재한다.
- 하지만 이번 샘플에서는 `neutral 0.000002` 수준으로 거의 선택되지 않았다.
- 즉 `중립 포함` 모델이지만, 실제 예측은 매우 강하게 `분노`로 쏠렸다.

### 3. `audio_text_fusion`

- 음성 모델의 `분노` 성향이 강하게 들어갔다.
- 텍스트 모델은 전사문 `프로젝트를 진행하며 바쁘게 지내고 있어요`를 `joy` 쪽으로 일부 해석한 것으로 보인다.
- 결과적으로 최종 fusion은 `anger 0.652377`, `joy 0.326060`으로 완화됐지만 `neutral`은 거의 나오지 않았다.

## 관찰 포인트

- 세 모델 모두 이번 단일 샘플에서 `중립`으로 보지 않았다.
- `sad_sample`에서도 `슬픔`으로 안정적으로 분류하지 못했다.
- `v1`은 `중립` 자체가 없어서 비교 기준상 한계가 명확하다.
- `v3`는 `중립`이 있지만 실제 추론은 `분노` 편향이 강했다.
- `fusion`은 단일 음성 모델보다 다소 완화되지만, 아직 `중립`과 `슬픔` 판별 안정성은 낮다.

## 현재 결론

`중립` 감정을 고려해야 한다면, 현재 3개 중 실무 비교 대상으로 남길 수 있는 것은 아래 둘이다.

- `wav2vec2_xlsr_ko_v3`
- `audio_text_fusion`

`wav2vec2_xlsr_ko_v1`은 `중립` 라벨 부재 때문에 인터뷰용 감정 분석 후보로는 우선순위를 낮추는 편이 맞다.

다만 현재 두 샘플 기준으로는 `v3`와 `audio_text_fusion`도 실제 기대 감정을 잘 맞춘다고 보기 어렵다. 즉 "후보를 더 좁혔다" 수준이지, "바로 도입 가능하다"는 뜻은 아니다.

## 한계

- 아직 샘플 수가 매우 적어서 일반화 판단은 불가능하다.
- 공개 체크포인트의 학습 데이터 편향이 결과에 그대로 반영될 수 있다.
- 멀티모달 모델은 공개 end-to-end 체크포인트가 아니라 PoC용 fusion 방식이라, 정식 모델과 동일하게 보면 안 된다.

## 결과 파일

- JSON 결과: [emotion_experiment.json](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/models/artifacts/emotion_experiment.json)
- sad sample JSON 결과: [emotion_experiment_sad_sample.json](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/models/artifacts/emotion_experiment_sad_sample.json)
- 실행 스크립트: [emotion_experiment.py](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/models/emotion_experiment.py)

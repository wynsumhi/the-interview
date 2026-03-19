# 매거진 인터뷰 상태 분석 PoC 상세 문서

작성일: 2026-03-19

## 목적

기존 공개 음성 감정 분류 모델은 현재 샘플 기준으로 `중립`이나 `슬픔` 성향 발화를 `anger` 또는 `surprise`로 과하게 오분류했다.

그래서 이번 PoC는 "감정 1개 강제 분류"를 버리고, 매거진 인터뷰에 더 맞는 상태 라벨을 사용해 아래 세 신호를 조합하는 방식으로 전환했다.

1. 전사 텍스트 의미
2. 음성 prosody 특징
3. rule-based fusion

핵심 원칙:
- 텍스트는 "무슨 이야기를 하고 있는지"를 본다.
- 음성은 "어떤 톤으로 말하고 있는지"를 본다.
- 최종 상태는 텍스트를 주 신호로, 음성을 보정 신호로 사용한다.

## 현재 상태 라벨

현재 PoC는 아래 5개 상태를 사용한다.

- `calm`
- `tense`
- `anxious`
- `low`
- `engaged`

각 의미:

- `calm`: 감정이 과하게 튀지 않고 안정적이며 차분한 상태
- `tense`: 살짝 굳어 있거나 압박, 예민함, 긴장감이 드러나는 상태
- `anxious`: 불안, 걱정, 긴장이 의미적으로도 비교적 분명한 상태
- `low`: 침체, 처짐, 무기력, 가라앉은 상태
- `engaged`: 주제에 관심과 몰입이 있고, 이야기를 풀어내는 에너지가 느껴지는 상태

매거진 인터뷰 기준으로 `engaged`를 둔 이유:
- 채용 인터뷰처럼 `confident`가 핵심인 상황이 아니다
- 답변의 온도, 관심, 회상, 몰입감이 더 중요한 경우가 많다

## 전체 파이프라인

입력:
- `wav` 파일
- 전사문

흐름:
1. 오디오를 읽어 frame 단위로 자른다
2. frame 기반 prosody feature를 계산한다
3. 전사문에서 상태 관련 키워드를 찾는다
4. 텍스트 점수와 음성 점수를 각각 만든다
5. 텍스트 0.65, 음성 0.35 가중치로 fusion 한다
6. 보정 규칙을 추가 적용한다
7. 정규화 후 최종 상태를 반환한다

구현 파일:
- [interview_state_experiment.py](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/models/interview_state_experiment.py)

## 음성 prosody 특징 추출 방식

외부 대형 음성 감정 모델 없이, `numpy`와 `soundfile`만으로 계산 가능한 특징만 사용했다.

### 1. Framing

- frame 길이: `25ms`
- hop 길이: `10ms`

즉, 음성을 25ms 단위 창으로 자르고 10ms씩 이동하면서 연속 frame을 만든다.

### 2. RMS

각 frame에서 아래 값을 계산한다.

```text
RMS = sqrt(mean(frame^2))
```

의미:
- 음성 에너지 크기
- 작으면 조용하고 평탄한 발화
- 크고 분산이 크면 억양 변화가 큰 발화

현재 통계:
- `rms_mean`
- `rms_std`

### 3. ZCR

각 frame에서 부호가 바뀌는 비율을 계산한다.

```text
ZCR = zero crossing count / frame_length
```

의미:
- 파형의 거칠기
- 현재는 보조 feature로 저장만 하고, 강한 rule에는 직접 거의 쓰지 않는다

저장값:
- `zcr_mean`

### 4. Silence Ratio

RMS 하위 25퍼센타일을 기준으로 silence threshold를 만들고, 그보다 작은 frame 비율을 계산한다.

개념:

```text
silence_threshold = max(0.01, percentile(rms, 25) * 0.8)
silence_ratio = mean(rms < silence_threshold)
```

의미:
- pause가 얼마나 많은지
- silence가 많으면 `low` 쪽 가능성을 본다

저장값:
- `silence_ratio`
- `voiced_ratio = 1 - silence_ratio`

### 5. Speech Rate Proxy

정교한 음절 수 기반 속도 추정은 하지 않고, voiced ratio를 duration으로 나눈 단순 proxy를 사용한다.

```text
speech_rate_proxy = voiced_ratio / duration_sec
```

의미:
- 말이 얼마나 빽빽하게 이어지는지 보는 거친 지표
- silence가 적고 계속 이어지면 높아짐

현재는 `tense` 보정에만 약하게 사용한다.

### 6. Pitch Mean / Std

각 voiced frame에서 autocorrelation 기반 pitch를 추정했다.

절차:
1. frame 평균을 뺀다
2. autocorrelation 계산
3. 사람 목소리로 볼 수 있는 lag 범위만 탐색
4. peak가 충분히 강할 때만 pitch로 인정
5. mean/std 계산

의미:
- `pitch_mean_hz`: 전체적으로 높은 톤인지
- `pitch_std_hz`: 피치 변동이 큰지

실무 해석:
- 평균 pitch가 높으면 긴장 가능성
- 변동폭이 크면 긴장 신호가 커질 수 있음
- 낮고 안정적이면 `calm` 가능성이 높음

저장값:
- `pitch_mean_hz`
- `pitch_std_hz`

## 텍스트에서 뽑아낸 정보

텍스트는 현재 모델 기반이 아니라 keyword rule 기반이다.

### 기본 원칙

- 기본 점수는 `calm = 0.7`
- 상태 키워드가 등장하면 관련 상태 점수를 올린다
- 텍스트 의미를 주 신호로 보기 때문에, 강한 표현이 있으면 그쪽으로 기운다

### 상태별 키워드

`anxious`
- `긴장`
- `불안`
- `떨`
- `걱정`
- `신경 쓰`
- `부담`
- `초조`
- `무섭`

`low`
- `우울`
- `슬프`
- `허무`
- `힘들`
- `지치`
- `외롭`
- `공허`
- `무기력`
- `속상`

`engaged`
- `좋았`
- `재밌`
- `흥미`
- `의미`
- `몰입`
- `재미있`
- `인상적`
- `즐거`
- `반갑`

`tense`
- `바쁘`
- `피곤`
- `압박`
- `정신없`
- `예민`

### 점수 규칙

- 일반 키워드가 나오면 해당 상태에 `+1.0`
- `tense`는 너무 쉽게 올라가지 않도록 키워드당 `+0.35`
- `anxious` 키워드가 있으면 `tense`도 `+0.5`
- `low` 키워드가 있으면 `calm`을 `-0.2`
- `engaged` 키워드가 있으면 `calm`을 `-0.1`

즉 텍스트 규칙은 `calm 기본값 + 상태 키워드 편향` 구조다.

## 음성에서 뽑아낸 상태 신호

초기값:
- `calm = 0.35`

규칙:

1. `pitch_mean_hz > 210`
- `anxious += 0.5`
- `tense += 0.3`

2. `pitch_std_hz > 45`
- `anxious += 0.5`

3. `silence_ratio > 0.45`
- `low += 0.6`
- `calm += 0.1`

4. `silence_ratio < 0.2` and `speech_rate_proxy > 0.18`
- `tense += 0.5`

5. `rms_mean < 0.035` and `pitch_std_hz < 25` and `silence_ratio < 0.35`
- `calm += 0.7`

6. `rms_mean < 0.03` and `silence_ratio > 0.35`
- `low += 0.4`

7. `0.03 <= rms_mean <= 0.06` and `10 <= pitch_std_hz < 28`
- `engaged += 0.25`

직관적으로 보면:
- 높은 평균 pitch: 긴장 가능성
- 큰 pitch 변동: 긴장 가능성
- pause 많음 + 낮은 에너지: `low` 가능성
- 적당한 에너지와 활기: `engaged` 보조 신호
- 낮은 에너지 + 작은 변동 + 적당한 silence: `calm` 가능성

## Rule-Based Fusion 방식

### 1. 기본 가중 합

- 텍스트: `0.65`
- 음성: `0.35`

즉 최종 상태는 기본적으로 텍스트 의미를 더 신뢰한다.

이렇게 한 이유:
- 매거진 인터뷰 발화는 말의 내용이 상태 해석에 매우 중요하다
- 음성만 보면 과오분류가 크다
- 음성은 긴장감, 차분함, 가라앉음, 활기를 보정하는 용도로 더 적합하다

### 2. 보정 규칙

1. 텍스트가 `calm`이고 음성이 `calm` 또는 `tense`
- `calm += 0.1`

2. 텍스트가 `low`이고 음성이 `low` 또는 `calm`
- `low += 0.08`

3. 텍스트가 `anxious`이고 음성이 `anxious` 또는 `tense`
- `anxious += 0.08`

4. 텍스트가 `engaged`이고 음성이 `calm` 또는 `engaged`
- `engaged += 0.05`

즉 양쪽 신호가 비슷한 방향을 가리킬 때 그 상태를 조금 더 밀어준다.

### 3. 정규화

최종 점수는 합계가 1이 되도록 normalize 한다.

## 샘플별 결과 해석

### 1. calm으로 본 샘플

파일:
- [37f21b4a-77af-473d-8178-92162568d776.wav](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/data/37f21b4a-77af-473d-8178-92162568d776.wav)

전사:
- `프로젝트를 진행하며 바쁘게 지내고 있어요`

오디오 feature:
- `duration_sec = 4.14`
- `rms_mean = 0.048692`
- `rms_std = 0.045836`
- `zcr_mean = 0.120240`
- `silence_ratio = 0.201456`
- `voiced_ratio = 0.798544`
- `speech_rate_proxy = 0.192885`
- `pitch_mean_hz = 158.769264`
- `pitch_std_hz = 54.023779`

텍스트 해석:
- `바쁘`가 잡혀 `tense`가 일부 올라감
- 하지만 기본 `calm`이 더 강해서 최종 텍스트는 `calm`

텍스트 점수:
- `calm 0.666667`
- `tense 0.333333`

음성 해석:
- `pitch_std_hz > 45`라서 긴장 신호가 있다
- `speech_rate_proxy`도 비교적 높은 편이라 약한 `tense` 신호가 있다
- 그래서 음성은 `anxious` top

음성 점수:
- `anxious 0.588235`
- `calm 0.205882`
- `tense 0.205882`

fusion 결과:
- 텍스트를 더 강하게 반영해서 최종 `calm`
- 즉 의미는 차분하고, 말투에는 약간 긴장감이 섞인 상태로 정리됨

최종 점수:
- `calm 0.577451`
- `tense 0.289215`
- `anxious 0.133333`

### 2. sad_sample

파일:
- [sad_sample.wav](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/data/sad_sample.wav)

전사:
- `그냥 모든 행동이 신경 쓰이는 사람이 있어요. 내가 하는 행동이든, 그 사람이 하는 행동이든.`

오디오 feature:
- `duration_sec = 8.440317`
- `rms_mean = 0.056416`
- `rms_std = 0.049906`
- `zcr_mean = 0.076464`
- `silence_ratio = 0.275534`
- `voiced_ratio = 0.724466`
- `speech_rate_proxy = 0.085834`
- `pitch_mean_hz = 217.072144`
- `pitch_std_hz = 37.187051`

텍스트 해석:
- `신경 쓰`가 `anxious` 키워드에 걸림
- 이 때문에 `anxious`와 `tense`가 같이 올라감
- 텍스트만 보면 `low`보다는 `불안/예민` 쪽에 더 가깝다

텍스트 점수:
- `anxious 0.454545`
- `calm 0.318182`
- `tense 0.227273`

음성 해석:
- `pitch_mean_hz > 210`이라 긴장 쪽 가중치가 붙음
- silence 비율이 아주 높지는 않아 `low` 규칙은 강하게 발동하지 않음
- 그래서 음성도 `anxious` top

음성 점수:
- `anxious 0.434783`
- `calm 0.304348`
- `tense 0.260870`

fusion 결과:
- 텍스트와 음성이 둘 다 `anxious`에 합의
- 그래서 보정 규칙까지 들어가 최종 `anxious`

최종 점수:
- `anxious 0.488545`
- `calm 0.290130`
- `tense 0.221326`

이 샘플은 파일명이 `sad_sample`이지만, 현재 규칙상 전사 의미와 prosody를 같이 보면 `low`보다는 `anxious`에 가깝게 해석된다.

## 실행 시간 측정

측정 방식:
- `PowerShell Measure-Command`로 스크립트 전체 실행 시간 측정
- 포함 범위: Python 프로세스 시작, 오디오 읽기, feature 추출, 텍스트 분석, fusion, JSON 저장

측정 결과:

| 샘플 | 길이 | 전체 실행 시간 |
| --- | --- | --- |
| `37f21b4a-77af-473d-8178-92162568d776.wav` | 약 `4.14초` | 약 `277 ms` |
| `sad_sample.wav` | 약 `8.44초` | 약 `369 ms` |

해석:
- 오디오 길이가 늘어도 실행 시간은 1초 미만으로 유지됐다
- 현재 구조는 실험용으로 충분히 가볍다
- 이 수치는 `STT` 시간은 포함하지 않는다

## 현재 PoC의 장점

- 공개 음성 감정 모델처럼 `anger`로 과하게 치우치지 않는다
- 왜 그런 결과가 나왔는지 근거를 설명할 수 있다
- 매거진 인터뷰 맥락에서 `감정`보다 `상태`를 더 자연스럽게 표현할 수 있다
- `ai/app`을 건드리지 않고 `tests` 안에서 검증 가능하다

## 현재 PoC의 한계

- keyword rule 기반이라 텍스트 의미를 깊게 이해하지는 못한다
- pitch 추정이 단순 autocorrelation 기반이라 정교하지 않다
- `low`와 `anxious`의 경계가 모호할 수 있다
- 현재 임계값은 샘플 수가 적은 상태의 초기값이다

## 다음 보강 방향

1. 정답 라벨이 붙은 샘플셋을 만든다
2. `low`, `anxious`, `calm` 경계 규칙을 더 세분화한다
3. speech rate를 음절 기반 지표로 개선한다
4. pitch 외에 jitter, shimmer, pause length 같은 feature를 추가한다
5. 텍스트 규칙 대신 한국어 문장 임베딩이나 소형 분류기를 붙일 수 있다

## 관련 결과 파일

- [interview_state_neutral_sample.json](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/models/artifacts/interview_state_neutral_sample.json)
- [interview_state_sad_sample.json](C:/Users/SSAFY/Desktop/S14P21A307/ai/tests/models/artifacts/interview_state_sad_sample.json)

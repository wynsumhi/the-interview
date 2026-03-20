# 한국어 음성 감정 분석 모델 정리

작성일: 2026-03-19

## 목적

`ai/app` 구현에 들어가기 전에, `ai/tests`에서 먼저 검증할 한국어 음성 감정 분석 후보 모델을 비교하기 위한 정리 문서다.

이 문서는 "지금 바로 붙여볼 수 있는 공개 체크포인트"와 "구조적으로 더 유망하지만 별도 학습/재현이 필요한 접근"을 구분해서 본다.

## 먼저 정리할 기준

한국어 음성 감정 분석 모델은 보통 아래 기준으로 평가해야 한다.

- 한국어 음성에 맞는 사전학습 백본인지
- 감정 라벨 체계가 우리 서비스 목적과 맞는지
- 공개 체크포인트가 바로 추론 가능한 상태인지
- 실시간 추론이 가능한 크기인지
- 인터뷰 음성처럼 긴 발화, 잡음, 망설임, 반말/존댓말, 낮은 감정 강도에 견디는지
- 단일 음성만 쓰는지, 텍스트 전사까지 함께 쓰는지

## 후보군 요약

| 구분 | 대표 후보 | 장점 | 한계 | 적합한 용도 |
| --- | --- | --- | --- | --- |
| 한국어 전용 음성 백본 + 감정 분류 | `HyunaZ/hubert_emotion` | 한국어 HuBERT 기반, 구조가 단순함 | 공개된 평가 정보가 제한적 | 1차 PoC |
| 한국어/다국어 wav2vec2 감정 분류 체크포인트 | `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition*` | 바로 테스트 가능, 분류기 포함 | 데이터셋/일반화 정보가 부족함 | 빠른 기능 검증 |
| 한국어 HuBERT/Wav2Vec2 백본 직접 파인튜닝 | `team-lucid/hubert-*-korean` 기반 재학습 | 한국어 음향 표현이 강함 | 별도 학습 파이프라인 필요 | 중기적 정식 모델 |
| 멀티모달 모델 | KoELECTRA + HuBERT 계열 | 텍스트+음성 결합으로 감정 인식력 향상 가능 | 전사 품질에 영향, 구현 복잡 | 인터뷰 서비스 고도화 |
| 고전 음향 특징 + CNN/BiLSTM/앙상블 | MFCC, mel-spectrogram 기반 2-stream | 설명 가능성, 경량화 여지 | 최신 SSL 대비 성능 열세 가능 | 온디바이스/경량 실험 |

## 모델별 특징

### 1. `HyunaZ/hubert_emotion`

개요:
- Hugging Face 공개 체크포인트
- `team-lucid/hubert-base-korean`을 베이스로 사용한 한국어 감정 분류 모델

특징:
- 한국어용 HuBERT 백본을 사용하므로, 영어 SER 모델을 그대로 쓰는 것보다 한국어 음운 특성을 반영할 가능성이 높다.
- raw waveform 입력 기반이라 전통적인 MFCC 수작업 특징 없이도 실험할 수 있다.
- 구조가 단순해서 `tests`에서 inference smoke test를 만들기 쉽다.

장점:
- 한국어 음성 백본 기반이라는 점이 가장 명확하다.
- PoC 단계에서 "한국어 입력 -> 감정 라벨" 경로를 가장 자연스럽게 검증할 수 있다.

주의점:
- 모델 카드에 데이터셋, 라벨 정의, 실제 운영 환경 한계가 충분히 상세하지 않다.
- 인터뷰 상황의 낮은 강도 감정이나 복합 감정에서 얼마나 안정적인지 근거가 부족하다.

추천도:
- 가장 먼저 붙여볼 1순위 후보.

### 2. `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition`

개요:
- Hugging Face 공개 체크포인트
- Wav2Vec2/XLSR 계열을 한국어 감정 인식으로 파인튜닝한 모델들
- 공개된 변형이 여러 개 있으며, 정확도 수치가 모델 카드마다 다르다

특징:
- 다국어/한국어 음성 표현을 활용한 분류기라서 바로 추론 실험이 쉽다.
- 일부 모델 카드에는 validation accuracy가 높게 적혀 있지만, 데이터 분할 방식과 일반화 가능성은 별도 검증이 필요하다.

장점:
- 바로 테스트에 붙여볼 수 있다.
- Hugging Face 기반 배포/로컬 추론 흐름을 그대로 재사용하기 쉽다.

주의점:
- 모델 카드 정보가 매우 얕고, 어떤 한국어 감정 데이터로 학습했는지 불명확한 경우가 있다.
- accuracy 수치만 보고 채택하면 과적합 가능성을 놓칠 수 있다.
- 실제 인터뷰 음성은 낭독형 감정 데이터보다 훨씬 어렵다.

추천도:
- 빠른 프로토타입 용도로는 좋지만, 정식 채택 전 재검증이 필요하다.

### 3. `team-lucid/hubert-base-korean`, `hubert-large-korean`, `hubert-xlarge-korean`

개요:
- 한국어 HuBERT 사전학습 백본
- 감정 분류 모델이 아니라 feature extractor 역할

특징:
- 약 4,000시간 규모의 한국어 음성으로 사전학습된 공개 백본이다.
- 감정 분류 헤드를 별도로 붙여 KEMDy20 같은 한국어 감정 데이터셋으로 파인튜닝하는 방식이 가장 정석적이다.

장점:
- "한국어 음성에 맞는 표현"과 "우리 태스크에 맞는 감정 헤드"를 분리해서 설계할 수 있다.
- 장기적으로는 현재 서비스 음성 도메인에 맞춰 재학습하기 가장 유연하다.

주의점:
- 지금 바로 감정 추론 결과를 얻는 모델은 아니다.
- base/large/xlarge에 따라 성능과 비용 차이가 커진다.

실무 해석:
- `base`: 테스트와 실험 속도에 유리
- `large`: 성능과 비용 균형 후보
- `xlarge`: GPU 비용이 커서 초기 검증 단계에는 과할 수 있음

추천도:
- 중기적으로 가장 유망한 정식 노선.

### 4. Wav2Vec2 / HuBERT 기반 직접 파인튜닝 접근

개요:
- 공개 논문과 벤치마크 전반에서 음성 감정 인식은 최근 SSL 백본 기반 접근이 주류다.
- 영어권뿐 아니라 비영어권에서도 HuBERT, Wav2Vec2 계열이 strong baseline으로 많이 쓰인다.

특징:
- raw waveform에서 의미 있는 음향 표현을 잘 뽑는다.
- 감정 데이터가 작아도 사전학습 표현 덕분에 classical feature 기반보다 유리한 경우가 많다.

장점:
- 우리 라벨 체계에 맞춘 재학습이 가능하다.
- 인터뷰 특화 라벨 예: `stable`, `nervous`, `confident`, `depressed-tone` 같은 도메인 라벨로 확장하기 쉽다.

주의점:
- 공개 체크포인트를 바로 붙이는 것보다 준비 비용이 더 든다.
- 라벨 설계와 데이터 품질이 성능을 크게 좌우한다.

추천도:
- 서비스 정확도를 중요하게 보면 결국 이 방향으로 갈 가능성이 높다.

### 5. 멀티모달 접근: 음성 + 텍스트 전사

개요:
- 한국어 감정 인식 연구에서는 HuBERT 계열 음성 표현과 KoELECTRA 계열 텍스트 표현을 결합한 멀티모달 모델이 단일 모달보다 더 좋은 결과를 보인 사례가 있다.

특징:
- 음성은 억양, 세기, 속도, 떨림을 보고
- 텍스트는 단어 선택, 부정 표현, 응답 태도, 회피성 표현을 본다

장점:
- 인터뷰 도메인에서는 음성만 보는 것보다 실제 감정/상태 해석력이 높아질 가능성이 크다.
- 예를 들어 음성은 차분하지만 텍스트는 불안 표현이 강한 케이스를 잡아내기 쉽다.

주의점:
- STT 품질이 낮으면 텍스트 모달이 오히려 노이즈가 된다.
- 실시간 파이프라인 복잡도가 커진다.

추천도:
- 2단계 이상 고도화에서 고려할 가치가 높다.

### 6. MFCC / mel-spectrogram + CNN/BiLSTM 계열

개요:
- 한국어 음성 감정 인식 논문에서 전통적인 음향 특징과 딥러닝 분류기를 조합한 2-stream, ensemble 방식도 꾸준히 쓰였다.

특징:
- MFCC, mel-spectrogram, prosody 같은 특징을 분리 추출한 뒤 CNN, BiLSTM, 앙상블로 결합한다.

장점:
- 추론 구조가 비교적 해석 가능하다.
- 리소스가 제한된 환경에서는 SSL 대형 모델보다 가벼울 수 있다.

주의점:
- 최신 SSL 백본 대비 표현력이 부족할 수 있다.
- 특징 추출 파이프라인 관리 비용이 추가된다.

추천도:
- 경량 베이스라인이 필요할 때만 우선순위가 있다.

## 데이터셋 관점 메모

모델 선택보다 더 중요한 것은 어떤 한국어 감정 데이터셋으로 평가했는가다.

특히 아래 이슈를 꼭 봐야 한다.

- 낭독형 감정 데이터인지, 자연 대화형 데이터인지
- 단일 라벨인지, 복합 감정/다중 라벨인지
- 남녀/연령/녹음 환경 편향이 큰지
- 인터뷰와 비슷한 발화 길이와 말투가 있는지

한국어 감정 인식 연구에서 자주 언급되는 데이터셋 중 하나는 `KEMDy20`이며, 멀티모달 감정 인식 연구에도 널리 사용된다. 다만 실제 인터뷰 환경과 완전히 같지는 않다.

## 현재 시점 추천 우선순위

### 빠르게 `tests`에서 붙여볼 후보

1. `HyunaZ/hubert_emotion`
2. `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition`

이 둘은 공개 체크포인트라서 테스트 코드에서 바로 로딩하고 결과 포맷을 검증하기 쉽다.

### 정식 구현 후보

1. `team-lucid/hubert-base-korean` 또는 `hubert-large-korean`을 백본으로 사용
2. 한국어 감정 데이터셋으로 별도 파인튜닝
3. 필요하면 STT 텍스트를 결합한 멀티모달 구조로 확장

## 우리 프로젝트 기준 제안

현재 목표가 "`ai/app`은 건드리지 않고 `ai/tests`에서 먼저 기능 검증"이라면, 아래 순서가 가장 실용적이다.

1. 공개 체크포인트 2개를 골라 추론 스모크 테스트를 만든다.
2. 출력 포맷을 `label`, `score`, `top_k` 형태로 고정한다.
3. 샘플 음성 몇 개로 결과 일관성을 본다.
4. 그 뒤에 정식 백본 재학습 필요성을 판단한다.

즉, 지금 단계에서는 "최고 성능 모델 선정"보다 "테스트에서 바로 재현 가능한 모델 선정"이 더 중요하다.

## 결론

현재 공개 자료 기준으로 보면, 한국어 음성 감정 분석의 실용적인 출발점은 아래 두 갈래다.

- 바로 붙이는 PoC: `HyunaZ/hubert_emotion`, `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition`
- 중기 정식화: `team-lucid/hubert-*-korean` 백본 + 한국어 감정 데이터로 재학습

인터뷰 서비스처럼 미묘한 정서 상태를 다루려면 최종적으로는 "음성 단일 모달"만으로는 한계가 있을 가능성이 높다. 장기적으로는 STT 텍스트와 결합한 멀티모달 접근이 더 적합하다.

## 출처

- Hugging Face, `HyunaZ/hubert_emotion`: https://huggingface.co/HyunaZ/hubert_emotion
- Hugging Face, `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition`: https://huggingface.co/jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition
- Hugging Face, `jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3`: https://huggingface.co/jungjongho/wav2vec2-xlsr-korean-speech-emotion-recognition3
- Hugging Face, `team-lucid/hubert-large-korean`: https://huggingface.co/team-lucid/hubert-large-korean
- Hugging Face, `team-lucid/hubert-xlarge-korean`: https://huggingface.co/team-lucid/hubert-xlarge-korean
- HuBERT paper: https://arxiv.org/abs/2106.07447
- Wav2Vec 2.0 paper: https://arxiv.org/abs/2006.11477
- KoHMT: A Multimodal Emotion Recognition Model Integrating KoELECTRA, HuBERT with Multimodal Transformer: https://www.mdpi.com/2079-9292/13/23/4674
- Multi-Label Emotion Recognition of Korean Speech Data Using Deep Fusion Models: https://www.mdpi.com/2076-3417/14/17/7604
- Speech Emotion Recognition Based on Two-Stream Deep Learning Model Using Korean Audio Information: https://doi.org/10.3390/app13042167
- KEMDy20가 언급된 멀티모달 감정 인식 정리 자료: https://www.mdpi.com/2227-7390/13/12/1907

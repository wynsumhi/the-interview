# Title and Keyword Model Comprehensive Summary

## 목적

- 매거진 타이틀 생성과 답변 키워드 생성 작업에 대해 `Claude Sonnet 4.5`, `Gemini 2.5 Flash Lite`, `GPT-5 mini`를 비교한다.
- 비교 기준은 `크레딧`, `응답 시간`, `실제 결과 품질`, `토큰 한도에서의 안정성`이다.

## 비교 대상

- Claude Sonnet 4.5: 30 크레딧
- Gemini 2.5 Flash Lite: 1 크레딧
- GPT-5 mini: 5 크레딧

## 테스트 입력

- 타이틀 생성: `tests/data/interview_raw.md` 전체 인터뷰 본문
- 키워드 생성 1: `Q1/A1`
- 키워드 생성 2: `Q5/A5`

## 테스트 기준

- 타이틀 생성 max token: 64
- 키워드 생성 max token: 32
- GPT-5 mini는 동일한 테스트 조건에서 `finish_reason=length`로 출력 실패
- 별도 확인 결과 GPT-5 mini는 더 넉넉한 completion budget이 있어야 안정적으로 출력할 가능성이 높음

## 타이틀 생성 비교

| 모델                  | 크레딧 | max token | 소요 시간 | 결과                                   | 메모                                                |
| --------------------- | -----: | --------: | --------: | -------------------------------------- | --------------------------------------------------- |
| Claude Sonnet 4.5     |     30 |        64 |     2.19s | A Solitary Return to Childhood's Stage | 인터뷰의 정서와 시간축을 더 깊게 반영한 느낌        |
| Gemini 2.5 Flash Lite |      1 |        64 |     1.27s | Unexpected Love for Musicals           | 핵심 주제는 잘 잡지만 보다 단편적이고 직관적인 제목 |
| GPT-5 mini            |      5 |        64 |     2.28s | 출력 실패                              | `finish_reason=length`, visible text 없음           |

## 키워드 생성 비교

### Q1 / A1

| 모델                  | 크레딧 | max token | 소요 시간 | 결과            | 메모                                      |
| --------------------- | -----: | --------: | --------: | --------------- | ----------------------------------------- |
| Claude Sonnet 4.5     |     30 |        32 |     1.83s | Musical Theater | 적절함                                    |
| Gemini 2.5 Flash Lite |      1 |        32 |     1.25s | Musical Theater | Claude와 동일 결과                        |
| GPT-5 mini            |      5 |        32 |     1.18s | 출력 실패       | `finish_reason=length`, visible text 없음 |

### Q5 / A5

| 모델                  | 크레딧 | max token | 소요 시간 | 결과                     | 메모                                      |
| --------------------- | -----: | --------: | --------: | ------------------------ | ----------------------------------------- |
| Claude Sonnet 4.5     |     30 |        32 |     2.94s | Coming Of Age Reflection | 기억의 의미와 성장 맥락까지 반영          |
| Gemini 2.5 Flash Lite |      1 |        32 |     1.07s | Personal Reflection      | 의미는 맞지만 더 일반적이고 얕은 편       |
| GPT-5 mini            |      5 |        32 |     1.26s | 출력 실패                | `finish_reason=length`, visible text 없음 |

## 종합 관찰

- 비용 효율성은 Gemini 2.5 Flash Lite가 압도적으로 우수하다.
- 응답 시간도 Gemini 2.5 Flash Lite가 가장 빠르다.
- 키워드 생성에서는 Gemini 2.5 Flash Lite가 충분히 좋은 결과를 보였다.
- 타이틀 생성에서는 Claude Sonnet 4.5가 인터뷰의 서사와 감정선을 더 깊게 반영했다.
- GPT-5 mini는 이번 테스트 조건에서는 출력 안정성이 떨어졌다.
- GPT-5 mini는 크레딧이 Gemini보다 5배 높고, 동일 조건에서 추가 토큰 여유까지 요구할 가능성이 있어 운영 효율 측면에서 불리하다.

## 운영 관점 해석

- `키워드 생성`은 Gemini 2.5 Flash Lite로 분리하는 것이 가장 합리적이다.
- `타이틀 생성`은 제목의 깊이와 결을 고려한다면 Claude가 적절하다.
- 하지만, 타이틀의 품질 차이가 서비스 핵심 경험에 직접 크게 영향을 주지 않는다.
- Gemini 2.5 Flash Lite는 주제를 충분히 전달하는 제목을 만들어 주며 비용과 속도 이점이 매우 크다.
- GPT-5 mini는 현재 시점에서 비용, 안정성, 토큰 효율을 함께 고려하면 우선 채택 후보로 보기 어렵다.

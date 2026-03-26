# Swipe Thresholds

## 목적

이 문서는 `Hand Landmarker` 기반 swipe 판정을 위한 초기 threshold 제안을 정의한다.
값은 첫 배포 전 shadow mode 또는 내부 테스트를 통해 조정할 수 있다.

## 입력 기준

프레임마다 아래 값을 계산한다.

- palm center x, y
- wrist 좌표
- index/middle/pinky MCP 평균 좌표
- handedness
- landmark detection confidence
- hand presence confidence

권장 palm center는 아래 landmark 평균으로 계산한다.

- wrist
- index MCP
- middle MCP
- ring MCP
- pinky MCP

## 시간 창

- 분석 window: `300ms` ~ `600ms`
- 최소 연속 유효 프레임: `4` 프레임 이상
- 권장 처리 FPS: `15` ~ `24`

## swipe 판정 초안

아래 조건을 모두 만족하면 swipe 후보로 본다.

1. 같은 손이 연속 프레임에서 안정적으로 검출될 것
2. palm center의 x축 누적 이동량이 충분할 것
3. 이동 방향 부호가 대부분 일관될 것
4. y축 변화량이 과도하지 않을 것
5. 손바닥이 열린 상태일 것

## 초기 threshold 제안

### 공통

- 최소 검출 confidence: `0.6`
- 최소 presence confidence: `0.6`
- 한 손만 추적: 가장 confidence 높은 손 선택
- 인식 후 cooldown: `1000ms`

### 방향성

- 기본 swipe 방향: 사용자 기준 오른쪽에서 왼쪽
- primary swipe: `delta_x <= -0.18` normalized screen width
- optional reverse swipe: `delta_x >= +0.18` normalized screen width
- 방향 일관성: 전체 구간의 `70%` 이상이 동일 방향

### 안정성

- 최대 허용 y축 변화: `0.10` normalized screen height
- 최대 허용 정지 프레임 비율: `40%`
- 화면 가장자리 접근 시 무효화: x가 `0.05` 미만 또는 `0.95` 초과인 프레임 다수 포함 시 reject

### 손 모양 조건

- 열린 손바닥 우선
- 손가락 펼침 판정이 불안정하면 최소한 `Closed_Fist`는 제외

## 종료 gesture threshold 초안

종료는 canned gesture 결과를 사용한다.

- 기본 gesture: `Victory`
- 최소 gesture confidence: `0.75`
- 1차 hold time: `1000ms`
- 2차 hold time: `1000ms`
- 1차 감지 후 안내 UI 문구 노출
- 2차 hold 완료 시 종료 실행
- 1차 감지 후 대기 시간 초과 시 종료 후보 상태 해제
- 종료 후보 상태에서는 다른 액션을 잠시 잠금

## 상태별 활성화

### `USER_RECORDING`

- swipe 활성화
- 종료 gesture 활성화

### `AI_SPEAKING`

- 질문 텍스트 표시 및 TTS 재생 중 상태
- swipe 비활성화
- 종료 gesture만 활성화

### `WAITING_AI`

- 답변 제출 후 다음 질문 생성 대기 중 상태
- 모든 gesture 비활성화

## 튜닝 우선순위

threshold 조정은 아래 순서로 진행한다.

1. 오탐 감소
2. 종료 gesture 안전성 확보
3. swipe 반응 속도 개선
4. 사용자 편의성 개선

## 조정 포인트

오탐이 많을 경우 아래 값을 먼저 보수적으로 조정한다.

- `delta_x` 증가
- hold time 증가
- cooldown 증가
- y축 허용폭 축소
- 열린 손 조건 강화

미탐이 많을 경우 아래 값을 완화한다.

- `delta_x` 감소
- 최소 연속 프레임 수 감소
- detection confidence 소폭 완화
- 처리 FPS 증가

## 권장 로그 항목

튜닝을 위해 아래 항목을 남기는 것을 권장한다.

- timestamp
- interview status
- gesture type
- delta_x
- delta_y
- duration_ms
- confidence
- accepted or rejected
- rejection reason

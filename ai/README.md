# AI Server for The Interview

The Interview는 AI가 질문하고 사용자가 음성으로 답하는 인터뷰 기반 자기 탐색 서비스입니다.

이 `ai/` 디렉토리는 음성 입력부터 응답 생성까지의 AI 파이프라인과 관련 API 서버를 담당합니다.

## Scope

This repository section is responsible for:

- Speech-to-Text (STT)
- Voice Emotion Analysis
- Guardrail / Sensitive Information Masking
- LLM-based question and response generation
- Text-to-Speech (TTS)
- Interview pipeline orchestration
- AI-related API endpoints

## Directory Structure

ai/
├── app/
│   ├── api/
│   ├── core/
│   ├── models/
│   ├── pipeline/
│   ├── prompts/
│   └── services/
├── tests/
├── requirements.txt
└── .env.example

## Pipeline Overview

speech -> STT -> emotion -> guardrail -> LLM -> TTS

## Run

python -m venv .venv

pip install -r requirements.txt

uvicorn app.main:app --reload

## Test

pytest
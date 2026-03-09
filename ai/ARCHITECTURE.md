# AI Architecture

## End-to-End Flow

User Speech
→ STT
→ Emotion Analysis
→ Guardrail
→ LLM
→ TTS
→ Output Speech / Structured Result

## Layered Architecture

API → Services → Pipeline → Models

### API Layer
Handles request/response.

### Service Layer
Business logic connecting models and pipeline.

### Model Layer
Provider wrappers (OpenAI, Whisper, etc).

### Pipeline Layer
Orchestrates full AI flow.
# 1. Build Stage
FROM python:3.11-slim AS builder

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir --user -r requirements.txt \
    && pip uninstall -y onnxruntime \
    && pip install --no-cache-dir --user --force-reinstall onnxruntime-gpu==1.24.4

COPY . .

# 2. Run Stage
FROM python:3.11-slim AS runner

WORKDIR /app

# 빌드 스테이지에서 설치된 패키지만 복사
COPY --from=builder /root/.local /root/.local
COPY --from=builder /app /app

# 환경 변수 설정 (설치된 패키지 경로 인식)
ENV PATH=/root/.local/bin:$PATH
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV DEFAULT_TTS_PROVIDER=cuda
ENV DEFAULT_TTS_AUTO_DOWNLOAD=true
ENV DEFAULT_TTS_PRELOAD_ON_STARTUP=true
ENV UVICORN_WORKERS=1

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers ${UVICORN_WORKERS}"]

# 1. Build Stage
FROM python:3.11-slim AS builder

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir --user -r requirements.txt

# 2. Run Stage
FROM python:3.11-slim AS runner

WORKDIR /app

# 빌드 스테이지에서 설치된 패키지만 복사
COPY --from=builder /root/.local /root/.local
COPY app ./app

# 환경 변수 설정 (설치된 패키지 경로 인식)
ENV PATH=/root/.local/bin:$PATH
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
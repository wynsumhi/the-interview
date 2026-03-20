# 1. Build Stage
FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04 AS builder

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3.11 python3-pip \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN python3.11 -m pip install --no-cache-dir --upgrade pip \
    && python3.11 -m pip install --no-cache-dir --user -r requirements.txt \
    && python3.11 -m pip uninstall -y onnxruntime \
    && python3.11 -m pip install --no-cache-dir --user --force-reinstall onnxruntime-gpu==1.24.4

COPY . .

# 2. Run Stage
FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04 AS runner

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3.11 python3-pip \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /root/.local /root/.local
COPY --from=builder /app /app

ENV PATH=/root/.local/bin:$PATH
ENV LD_LIBRARY_PATH=/usr/local/cuda/lib64:/usr/local/cuda/compat/lib:${LD_LIBRARY_PATH}
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV DEFAULT_TTS_PROVIDER=cuda
ENV DEFAULT_TTS_AUTO_DOWNLOAD=true
ENV DEFAULT_TTS_PRELOAD_ON_STARTUP=true
ENV UVICORN_WORKERS=1

EXPOSE 8000

CMD ["sh", "-c", "python3.11 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers ${UVICORN_WORKERS}"]

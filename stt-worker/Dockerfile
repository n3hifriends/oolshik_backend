FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    XDG_CACHE_HOME=/app/.cache

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        libgomp1 \
        curl \
        pkg-config \
        build-essential \
        libavcodec-dev \
        libavformat-dev \
        libavdevice-dev \
        libavutil-dev \
        libswscale-dev \
        libswresample-dev \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 10001 appuser

WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY src /app/src
RUN mkdir -p /app/.cache /app/tmp \
    && chown -R appuser:appuser /app

USER appuser

ENV TMP_DIR=/app/tmp \
    PYTHONPATH=/app/src

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl -fsS http://localhost:8081/healthz || exit 1

# GPU hint: use a CUDA base image and set DEVICE=cuda for faster-whisper.
# Example: FROM nvidia/cuda:12.1.0-runtime-ubuntu22.04 and install python3.11 + ffmpeg.

CMD ["python", "-m", "stt_worker.main"]

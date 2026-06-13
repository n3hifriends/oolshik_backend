# ── Build-time configuration ──────────────────────────────────────────────────
# COMPUTE=cpu  → CPU-only PyTorch (~800 MB, works on any EC2 instance)
# COMPUTE=gpu  → CUDA 12.1 PyTorch (requires nvidia-container-runtime on host)
#
# To build for GPU:
#   docker build --build-arg COMPUTE=gpu -t oolshik-stt-worker:gpu .
# ──────────────────────────────────────────────────────────────────────────────
ARG COMPUTE=cpu

# ═══════════════════════════════════════════════════════════════════════════════
# Stage 1 – builder
# Installs ALL Python packages, including packages like PyAV (av) that have no
# Linux binary wheel and must be compiled from source.  Build tools live here
# and are never copied into the final image.
# ═══════════════════════════════════════════════════════════════════════════════
FROM python:3.11-slim-bookworm AS builder

ARG COMPUTE=cpu

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        pkg-config \
        ffmpeg \
        libavcodec-dev \
        libavformat-dev \
        libavdevice-dev \
        libavutil-dev \
        libswscale-dev \
        libswresample-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY requirements.txt requirements-torch-cpu.txt requirements-torch-gpu.txt ./

# Isolated venv keeps the compiled packages in one directory that the final
# stage can simply copy in, without carrying any build tooling.
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH" \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

RUN pip install --prefer-binary -r requirements.txt \
    && if [ "$COMPUTE" = "gpu" ]; then \
         pip install --prefer-binary -r requirements-torch-gpu.txt; \
       else \
         pip install --prefer-binary -r requirements-torch-cpu.txt; \
       fi


# ═══════════════════════════════════════════════════════════════════════════════
# Stage 2 – final
# Clean runtime image: no compilers, no -dev headers.
# ffmpeg installs the shared libraries that av (compiled above) links against.
# ═══════════════════════════════════════════════════════════════════════════════
FROM python:3.11-slim-bookworm

ARG COMPUTE=cpu

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH" \
    XDG_CACHE_HOME=/app/.cache \
    HF_HOME=/models/hf \
    TMP_DIR=/app/tmp \
    PYTHONPATH=/app/src \
    STT_ENGINE=fasterwhisper \
    STT_DEFAULT_LANG=auto

# ffmpeg  – binary used by subprocess call + shared libs av needs at runtime
# libgomp1 – OpenMP, required by ctranslate2 / faster-whisper
# curl     – used by HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        libgomp1 \
        curl \
    && rm -rf /var/lib/apt/lists/*

# Pull in the fully-compiled Python environment from the builder stage
COPY --from=builder /opt/venv /opt/venv

RUN useradd -m -u 10001 appuser

WORKDIR /app

RUN mkdir -p /models/hf

COPY src /app/src

RUN mkdir -p /app/.cache /app/tmp /models/hf \
    && chown -R appuser:appuser /app /models/hf

USER appuser

# Expose which compute variant this image was built with
ENV COMPUTE_VARIANT=${COMPUTE}

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl -fsS http://localhost:8081/health || exit 1

CMD ["python", "-m", "stt_worker.main"]

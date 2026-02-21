FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    XDG_CACHE_HOME=/app/.cache \
    HF_HOME=/models/hf \
    TMP_DIR=/app/tmp \
    PYTHONPATH=/app/src \
    STT_ENGINE=indicconformer \
    STT_DEFAULT_LANG=mr \
    STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=false

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

ARG PRELOAD_ASR_MODEL=false
ARG ASR_MODEL_ID=ai4bharat/indic-conformer-600m-multilingual
ARG ASR_MODEL_REVISION=
ARG HF_TOKEN=

RUN mkdir -p /models/hf \
    && PRELOAD_ASR_MODEL="$PRELOAD_ASR_MODEL" ASR_MODEL_ID="$ASR_MODEL_ID" ASR_MODEL_REVISION="$ASR_MODEL_REVISION" HF_TOKEN="$HF_TOKEN" python - <<'PY'
import os
import sys
import traceback
from huggingface_hub import snapshot_download

if os.environ.get("PRELOAD_ASR_MODEL", "false").strip().lower() != "true":
    raise SystemExit(0)

repo_id = os.environ["ASR_MODEL_ID"]
revision = os.environ.get("ASR_MODEL_REVISION") or None
token = os.environ.get("HF_TOKEN") or None

print(f"Preloading ASR model: repo_id={repo_id}, revision={revision}", flush=True)
try:
    snapshot_download(
        repo_id=repo_id,
        revision=revision,
        token=token,
        local_dir="/models/hf/indic-conformer",
    )
except Exception as exc:  # noqa: BLE001
    print(f"Model preload failed: {exc}", file=sys.stderr, flush=True)
    traceback.print_exc()
    raise
PY

COPY src /app/src
RUN mkdir -p /app/.cache /app/tmp /models/hf \
    && chown -R appuser:appuser /app /models/hf

USER appuser

ENV ASR_MODEL_PATH=/models/hf/indic-conformer

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl -fsS http://localhost:8081/health || exit 1

CMD ["python", "-m", "stt_worker.main"]

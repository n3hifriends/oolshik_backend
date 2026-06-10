# STT Worker — AWS Deployment Guide

**Strategy:** small image in ECR + models downloaded at first run on EC2 into a persistent host-mounted cache.

Replace these placeholders wherever they appear:

| Placeholder      | Your value             |
| ---------------- | ---------------------- |
| `HF_TOKEN_VALUE` | HuggingFace token      |
| `AWS_ACCOUNT_ID` | `653895707563`         |
| `AWS_REGION`     | `ap-south-1`           |
| `ECR_REPO`       | `oolshik-stt-worker`   |
| `KAFKA_BROKERS`  | e.g. `10.20.0.13:9092` |

---

## Compute variants

| Variant | When to use          | Image size (approx) | EC2 instance types             |
| ------- | -------------------- | ------------------- | ------------------------------ |
| `cpu`   | MVP / cost-sensitive | ~1.2 GB             | t3.medium, c5.xlarge, m5.large |
| `gpu`   | Higher throughput    | ~3 GB               | g4dn.xlarge, g5.xlarge         |

---

## Quick reference

```
# LOCAL ──────────────────────────────────────────────────────────────
./scripts/build.sh                        # build cpu (default)
./scripts/build.sh gpu                    # build gpu
docker-compose up                         # run cpu locally
docker-compose -f docker-compose.yml \
               -f docker-compose.gpu.yml up   # run gpu locally

# PUSH TO ECR ─────────────────────────────────────────────────────────
./push-image.sh oolshik-stt-worker        # push cpu  → v<N>-cpu, latest-cpu
./push-image.sh oolshik-stt-worker gpu    # push gpu  → v<N>-gpu, latest-gpu

# EC2 ─────────────────────────────────────────────────────────────────
./scripts/run-ec2.sh start
./scripts/run-ec2.sh status
./scripts/run-ec2.sh logs
./scripts/run-ec2.sh restart
./scripts/run-ec2.sh stop
```

---

## 1. Build and push from your Mac

```bash
cd /path/to/oolshik-backend-otp/stt-worker

# CPU build (default — use this for MVP)
./push-image.sh oolshik-stt-worker

# GPU build (when moving to GPU instances)
./push-image.sh oolshik-stt-worker gpu
```

The script handles ECR login, auto-increments the version tag, and prints the full image URI on completion.

---

## 2. One-time EC2 setup

### 2a. Install Docker (if not already installed)

```bash
sudo apt-get update
sudo apt-get install -y docker.io awscli
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
# Log out and back in for group membership to take effect
```

### 2b. Create the env file (one time, stays on the instance)

```bash
sudo mkdir -p /etc/stt-worker

sudo tee /etc/stt-worker/env <<'EOF'
# Required
IMAGE_URI=653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:latest-cpu
KAFKA_BOOTSTRAP_SERVERS=10.20.0.13:9092
HF_TOKEN=REMOVED_HF_TOKEN

# Optional — defaults shown
COMPUTE=cpu
DEVICE=cpu
COMPUTE_TYPE=
MODELS_DIR=/opt/stt-worker/models
TMP_DIR=/tmp/stt-worker
AWS_REGION=ap-south-1
STT_ENGINE=indicconformer
STT_ENABLE_FALLBACK=true
STT_DEFAULT_LANG=auto
STT_AUTO_ROUTE_PRIMARY_LANGS=mr,hi
STT_AUTO_ROUTE_MIN_CONFIDENCE=0.30
STT_AUTO_ROUTE_INDIC_FALLBACK_LANG=mr
MODEL_SIZE=small
WORKER_CONCURRENCY=1
LOG_LEVEL=INFO
EOF

sudo chmod 600 /etc/stt-worker/env
```

### 2c. Prepare model cache directory

```bash
sudo mkdir -p /opt/stt-worker/models
sudo chown -R 10001:10001 /opt/stt-worker/models
```

The IndicConformer and FasterWhisper models download here on first start and are reused on every subsequent restart — no re-download needed.

### 2d. Copy run-ec2.sh to the instance

From your Mac:

```bash
scp -i your-key.pem \
    stt-worker/scripts/run-ec2.sh \
    ubuntu@<EC2_PUBLIC_IP>:/home/ubuntu/run-ec2.sh

ssh -i your-key.pem ubuntu@<EC2_PUBLIC_IP>
chmod +x ~/run-ec2.sh
```

---

## 3. Start the worker on EC2

```bash
~/run-ec2.sh start
```

The script:

1. Logs in to ECR using the instance IAM role
2. Pulls the image
3. Starts the container with `--restart unless-stopped`
4. Polls `http://localhost:8081/health` until the worker is ready (model download happens here on first run — may take a few minutes)

---

## 4. Verify startup

```bash
~/run-ec2.sh status
~/run-ec2.sh logs
```

In the logs you want to see:

- model download activity on first run (normal)
- `Worker ready` with `engine=indicconformer`
- fallback logs only after a primary IndicConformer failure

```bash
# Quick health check
curl -s http://localhost:8081/health
# Metrics endpoint
curl -s http://localhost:9108/metrics | grep stt_
```

---

## 5. Verify effective runtime config inside the container

```bash
sudo docker exec -it stt-worker sh -lc '
echo "ASR_MODEL_PATH=<$ASR_MODEL_PATH>"
echo "ASR_MODEL_ID=<$ASR_MODEL_ID>"
echo "HF_HOME=<$HF_HOME>"
echo "STT_DEFAULT_LANG=<$STT_DEFAULT_LANG>"
echo "STT_AUTO_ROUTE_PRIMARY_LANGS=<$STT_AUTO_ROUTE_PRIMARY_LANGS>"
echo "STT_AUTO_ROUTE_MIN_CONFIDENCE=<$STT_AUTO_ROUTE_MIN_CONFIDENCE>"
echo "STT_AUTO_ROUTE_INDIC_FALLBACK_LANG=<$STT_AUTO_ROUTE_INDIC_FALLBACK_LANG>"
echo "STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=<$STT_ALLOW_RUNTIME_MODEL_DOWNLOAD>"
echo "STT_ENABLE_FALLBACK=<$STT_ENABLE_FALLBACK>"
echo "COMPUTE_VARIANT=<$COMPUTE_VARIANT>"
python -c "import os; print(\"HF_TOKEN set =\", bool(os.getenv(\"HF_TOKEN\")))"
'
```

Expected:

```
ASR_MODEL_ID=<ai4bharat/indic-conformer-600m-multilingual>
HF_HOME=</models/hf>
STT_DEFAULT_LANG=<auto>
STT_AUTO_ROUTE_PRIMARY_LANGS=<mr,hi>
STT_AUTO_ROUTE_MIN_CONFIDENCE=<0.30>
STT_AUTO_ROUTE_INDIC_FALLBACK_LANG=<mr>
STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=<true>
STT_ENABLE_FALLBACK=<true>
COMPUTE_VARIANT=<cpu>
HF_TOKEN set = True
```

---

## 6. Diagnose IndicConformer load failure

If the worker falls back to FasterWhisper at startup or logs `MODEL_LOAD_FAILED`, run this inside the container:

```bash
sudo docker exec -it stt-worker sh -lc 'python - <<'"'"'PY'"'"'
import os, traceback
from stt_worker.transcribe.engine import IndicConformerEngine

print("ASR_MODEL_PATH =", os.getenv("ASR_MODEL_PATH"))
print("ASR_MODEL_ID   =", os.getenv("ASR_MODEL_ID"))
print("HF_HOME        =", os.getenv("HF_HOME"))
print("ALLOW_DOWNLOAD =", os.getenv("STT_ALLOW_RUNTIME_MODEL_DOWNLOAD"))
print("HF_TOKEN set   =", bool(os.getenv("HF_TOKEN")))

try:
    engine = IndicConformerEngine(
        model_id=os.getenv("ASR_MODEL_ID", "ai4bharat/indic-conformer-600m-multilingual"),
        revision=os.getenv("ASR_MODEL_REVISION") or None,
        decoding=os.getenv("ASR_DECODING", "rnnt"),
        allow_runtime_model_download=(
            os.getenv("STT_ALLOW_RUNTIME_MODEL_DOWNLOAD", "false").lower() == "true"
        ),
    )
    print("ENGINE LOAD OK:", engine.model_version)
except Exception as e:
    print("ENGINE LOAD FAILED:", repr(e))
    traceback.print_exc()
PY'
```

---

## 7. Send a test job

From the EC2 instance or any machine with Kafka access:

```bash
sudo docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server 10.20.0.13:9092 \
  --topic stt.jobs
```

Paste this payload (Ctrl+D to send):

```json
{
  "jobId": "11111111-1111-1111-1111-111111111111",
  "taskId": "22222222-2222-2222-2222-222222222222",
  "audioUrl": "https://github.com/samnaveenkumaroff/Indic-F5/raw/refs/heads/main/1.wav",
  "languageHint": "mr",
  "createdAt": "2026-04-27T12:00:00Z",
  "correlationId": "33333333-3333-3333-3333-333333333333"
}
```

Watch the result:

```bash
~/run-ec2.sh logs
```

---

## 8. Moving to GPU (zero code changes)

### 8a. Push the GPU image from your Mac

```bash
./push-image.sh oolshik-stt-worker gpu
# Prints: 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v1-gpu
```

### 8b. Install nvidia-container-toolkit on the GPU EC2 instance

```bash
# On the EC2 instance (g4dn / g5 class)
distribution=$(. /etc/os-release; echo $ID$VERSION_ID)
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor \
    -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/$distribution/libnvidia-container.list | \
    sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
    sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
sudo apt-get update
sudo apt-get install -y nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

### 8c. Update the env file and restart

```bash
sudo tee -a /etc/stt-worker/env <<'EOF'
IMAGE_URI=653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:latest-cpu
COMPUTE=cpu
DEVICE=cpu
EOF

~/run-ec2.sh restart
```

That is the only change required to switch from CPU to GPU.

---

## Notes

- `STT_DEFAULT_LANG=auto` — unknown language jobs use a FasterWhisper pre-pass before routing Marathi/Hindi to IndicConformer.
- `STT_ENABLE_FALLBACK=true` — FasterWhisper is used only when IndicConformer fails, not as default.
- `COMPUTE_TYPE` — leave blank; engines auto-select `int8` on CPU and `float16` on CUDA.
- First startup takes longer due to model download. Subsequent restarts are fast because `/opt/stt-worker/models` is persisted on the host.
- Rotate `HF_TOKEN` and any exposed credentials after initial setup.

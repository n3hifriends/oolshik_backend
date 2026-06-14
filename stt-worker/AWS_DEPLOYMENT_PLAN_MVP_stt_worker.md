Use this revised plan for `stt-worker` with **small image in ECR + IndicConformer downloaded at runtime from Hugging Face**.

**Strategy:** small image in ECR + models downloaded at first run on EC2 into a persistent host-mounted cache.

- `HF_TOKEN_VALUE`
- `AWS_ACCOUNT_ID=653895707563`
- `AWS_REGION=ap-south-1`
- `ECR_IMAGE=oolshik-stt-worker`
- `ECR_TAG=latest`

| Placeholder      | Your value             |
| ---------------- | ---------------------- |
| `HF_TOKEN_VALUE` | HuggingFace token      |
| `AWS_ACCOUNT_ID` | `653895707563`         |
| `AWS_REGION`     | `ap-south-1`           |
| `ECR_REPO`       | `oolshik-stt-worker`   |
| `KAFKA_BROKERS`  | e.g. `10.20.0.13:9092` |

Do **not** preload the model into the image.

```bash
cd /path/to/oolshik-backend-otp/stt-worker

# CPU build (default — use this for MVP)
./push-image.sh oolshik-stt-worker

docker buildx build \
  --platform linux/amd64 \
  -t 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v4 \
  --push .
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

sudo docker pull 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v4
```

## 3. Export the Hugging Face token on EC2

```bash
export HF_TOKEN='HF_TOKEN_VALUE'
```

## 4. Run `stt-worker` with runtime model download

```bash
~/run-ec2.sh status
~/run-ec2.sh logs
```

sudo docker run -d \
  --name stt-worker \
  --restart unless-stopped \
  -e KAFKA_BOOTSTRAP_SERVERS=10.20.0.13:9092 \
  -e STT_JOBS_TOPIC=stt.jobs \
  -e STT_RESULTS_TOPIC=stt.results \
  -e STT_DLQ_TOPIC=stt.jobs.dlq \
  -e STT_ENGINE=indicconformer \
  -e STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=true \
  -e ASR_MODEL_ID=ai4bharat/indic-conformer-600m-multilingual \
  -e ASR_MODEL_PATH= \
  -e HF_TOKEN="$HF_TOKEN" \
  653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v4
```

Important:

- `ASR_MODEL_PATH=` must stay empty. Do not mount `/models/hf/indic-conformer` in this mode.
- `HF_TOKEN` must be present when you run `sudo docker run`. If needed, use `sudo --preserve-env=HF_TOKEN docker run ...`.
- The first startup can take time because the worker downloads the model from Hugging Face.

## 5. Verify startup

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

- initial logs may show model download activity
- you want `Worker ready`
- `engine` should be `indicconformer`

## 6. Verify effective runtime config

```bash
sudo docker exec -it stt-worker sh -lc 'echo "ASR_MODEL_PATH=<$ASR_MODEL_PATH>"; echo "ASR_MODEL_ID=<$ASR_MODEL_ID>"; echo "STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=<$STT_ALLOW_RUNTIME_MODEL_DOWNLOAD>"; python - <<'"'"'"'"'"'"'"'"'PY'"'"'"'"'"'"'"'"'
import os
print("HF_TOKEN set =", bool(os.getenv("HF_TOKEN")))
PY'
```

You want:

- `ASR_MODEL_PATH=<>`
- `ASR_MODEL_ID=<ai4bharat/indic-conformer-600m-multilingual>`
- `STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=<true>`
- `HF_TOKEN set = True`

## 7. If it still falls back

Capture the actual engine-load error:

```bash
sudo docker exec -it stt-worker sh -lc 'python - <<'"'"'"'"'"'"'"'"'PY'"'"'"'"'"'"'"'"'
import os
import traceback
from stt_worker.transcribe.engine import IndicConformerEngine

print("ASR_MODEL_PATH =", os.getenv("ASR_MODEL_PATH"))
print("ASR_MODEL_ID   =", os.getenv("ASR_MODEL_ID"))
print("STT_ALLOW_RUNTIME_MODEL_DOWNLOAD =", os.getenv("STT_ALLOW_RUNTIME_MODEL_DOWNLOAD"))
print("HF_TOKEN set   =", bool(os.getenv("HF_TOKEN")))

try:
    engine = IndicConformerEngine(
        model_id=os.getenv("ASR_MODEL_ID", "ai4bharat/indic-conformer-600m-multilingual"),
        revision=os.getenv("ASR_MODEL_REVISION") or None,
        decoding=os.getenv("ASR_DECODING", "rnnt"),
        allow_runtime_model_download=(os.getenv("STT_ALLOW_RUNTIME_MODEL_DOWNLOAD", "false").lower() == "true"),
    )
    print("ENGINE LOAD OK", engine.model_version)
except Exception as e:
    print("ENGINE LOAD FAILED:", repr(e))
    traceback.print_exc()
    if getattr(e, "__cause__", None) is not None:
        print("\\nCAUSE:")
        traceback.print_exception(type(e.__cause__), e.__cause__, e.__cause__.__traceback__)
PY'
```

Then inspect:

```bash
sudo docker logs -n 200 stt-worker
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

- Rotate the exposed Hugging Face token and DB password after this.
- In this runtime-download mode, do not also configure a mounted local model path unless the worker code is explicitly updated to support it.

Use this revised plan for `stt-worker` with **small image in ECR + IndicConformer downloaded at runtime from Hugging Face**.

Replace these placeholders first:

- `HF_TOKEN_VALUE`
- `AWS_ACCOUNT_ID=653895707563`
- `AWS_REGION=ap-south-1`
- `ECR_IMAGE=oolshik-stt-worker`
- `ECR_TAG=latest`

## 1. Build and push the small image from your Mac

Do **not** preload the model into the image.

```bash
cd /Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/stt-worker

aws ecr get-login-password --region ap-south-1 | \
docker login --username AWS --password-stdin 653895707563.dkr.ecr.ap-south-1.amazonaws.com

docker buildx build \
  --platform linux/amd64 \
  -t 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v4 \
  --push .
```

## 2. Pull the image on EC2

```bash
aws ecr get-login-password --region ap-south-1 | \
sudo docker login --username AWS --password-stdin 653895707563.dkr.ecr.ap-south-1.amazonaws.com

sudo docker pull 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v4
```

## 3. Export the Hugging Face token on EC2

```bash
export HF_TOKEN='HF_TOKEN_VALUE'
```

## 4. Run `stt-worker` with runtime model download

```bash
sudo docker rm -f stt-worker || true

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
sudo docker ps -a
sudo docker logs -n 200 stt-worker
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

## Optional test job

```bash
sudo docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server 10.20.0.13:9092 \
  --topic stt.jobs
```

Paste:

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

Then watch:

```bash
sudo docker logs -f stt-worker
```

## Important

- Rotate the exposed Hugging Face token and DB password after this.
- In this runtime-download mode, do not also configure a mounted local model path unless the worker code is explicitly updated to support it.

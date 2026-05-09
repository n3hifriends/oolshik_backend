Use this revised plan for `stt-worker` with **small image in ECR + model downloaded on EC2 host**.

Replace these placeholders first:

- `HF_TOKEN_VALUE`
- `AWS_ACCOUNT_ID=653895707563`
- `AWS_REGION=ap-south-1`
- `ECR_IMAGE=oolshik-stt-worker`
- `ECR_TAG=v1`

## 1. Build and push the small image from your Mac

Do **not** preload the model.

```bash
cd /Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/stt-worker

aws ecr get-login-password --region ap-south-1 | \
docker login --username AWS --password-stdin 653895707563.dkr.ecr.ap-south-1.amazonaws.com

docker buildx build \
  --platform linux/amd64 \
  -t 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v1 \
  --push .
```

## 2. Pull the image on EC2

```bash
aws ecr get-login-password --region ap-south-1 | \
sudo docker login --username AWS --password-stdin 653895707563.dkr.ecr.ap-south-1.amazonaws.com

sudo docker pull 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v1
```

## 3. Download the IndicConformer model once on EC2 host

```bash
export HF_TOKEN='HF_TOKEN_VALUE'

sudo rm -rf /opt/oolshik/models/hf/indic-conformer
sudo mkdir -p /opt/oolshik/models/hf/indic-conformer

sudo docker run --rm \
  -e HF_TOKEN \
  -v /opt/oolshik/models/hf/indic-conformer:/model \
  python:3.11-slim bash -lc '
    pip install --no-cache-dir huggingface_hub &&
    python - <<'"'"'PY'"'"'
import os
from huggingface_hub import snapshot_download

snapshot_download(
    repo_id="ai4bharat/indic-conformer-600m-multilingual",
    token=os.environ["HF_TOKEN"],
    local_dir="/model",
)
print("download complete")
PY
  '

unset HF_TOKEN
```

## 4. Verify the model actually downloaded

```bash
ls -lah /opt/oolshik/models/hf/indic-conformer | head -50
test -f /opt/oolshik/models/hf/indic-conformer/config.json && echo "config.json present" || echo "config.json missing"
du -sh /opt/oolshik/models/hf/indic-conformer
find /opt/oolshik/models/hf/indic-conformer -type f | egrep 'bin|safetensors|pt|onnx'
```

You want:

- `config.json present`
- directory size much larger than a few KB
- at least one real weight/artifact file

## 5. Run `stt-worker` using the mounted local model

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
  -e STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=false \
  -e ASR_MODEL_PATH=/models/hf/indic-conformer \
  -v /opt/oolshik/models/hf/indic-conformer:/models/hf/indic-conformer \
  653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v1
```

## 6. Verify startup

```bash
sudo docker ps -a
sudo docker logs -n 200 stt-worker
```

Expected:

- no `IndicConformer init failed`
- `Worker ready`
- `engine` should be `indicconformer`

## 7. If it still falls back

Check the mounted model again:

```bash
find /opt/oolshik/models/hf/indic-conformer -maxdepth 2 -type f | sed -n '1,100p'
du -sh /opt/oolshik/models/hf/indic-conformer
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

Rotate the exposed Hugging Face token and DB password after this.

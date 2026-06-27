# Deployment Runner Plan

## Goal

Create one guided orchestration script for local development and EC2 deployment while still allowing individual services to be started, deployed, restarted, checked, or logged independently.

The script should make common workflows easy:

```bash
./scripts/run-all.sh local up all
./scripts/run-all.sh local up api
./scripts/run-all.sh local up kafka notification-worker
./scripts/run-all.sh local up mobile --platform ios

./scripts/run-all.sh ec2 deploy api
./scripts/run-all.sh ec2 deploy notification-worker
./scripts/run-all.sh ec2 restart api
./scripts/run-all.sh ec2 status all
```

It should not replace the existing service scripts immediately. First version should be a thin orchestrator over the scripts and Docker Compose commands that already exist.

## Current Scripts Reviewed

- `scripts/run-ec2.sh`
  - Runs the API container on EC2.
  - Pulls `oolshik-api` from ECR.
  - Loads `/etc/oolshik-backend/env`, with current fallback to `scripts/oolshik-backend.env`.
  - Mounts Firebase Admin SDK JSON when present.
  - Supports `start`, `stop`, `restart`, `status`, `logs`.

- `notification-worker/scripts/run-ec2.sh`
  - Runs the notification worker container on EC2.
  - Pulls `oolshik-notification-worker` from ECR.
  - Loads `/etc/notification-worker/env`.
  - Supports `start`, `stop`, `restart`, `status`, `logs`.

- `scripts/asg-api-user-data.sh`
  - EC2 Auto Scaling Group user-data for API.
  - Fetches backend env and Firebase JSON from AWS Secrets Manager.
  - Starts the API container.

- `notification-worker/scripts/asg-notification-worker-user-data.sh`
  - EC2 Auto Scaling Group user-data for notification worker.
  - Fetches worker env and Firebase JSON from AWS Secrets Manager.
  - Starts the notification worker container.

- `stt-worker/scripts/run-ec2.sh`
  - Runs the STT worker container on EC2.
  - Pulls `oolshik-stt-worker` from ECR.
  - Loads `/etc/stt-worker/env`.
  - Supports `start`, `stop`, `restart`, `status`, `logs`.
  - Supports CPU/GPU runtime selection through env such as `COMPUTE` and `DEVICE`.

- `stt-worker/scripts/user-data.sh`
  - EC2 user-data bootstrap for STT worker.
  - Uses Ubuntu-style `apt-get` installation.
  - Writes `/etc/stt-worker/env`.

- `stt-worker/scripts/build.sh`
  - Builds STT worker locally.
  - Produces `oolshik-stt-worker:local-cpu` or `oolshik-stt-worker:local-gpu`.

- `push-image.sh`
  - Builds and pushes an ECR image.
  - Current usage is `./push-image.sh <image-name>`.

- `notification-worker/push-image.sh`
  - Builds and pushes notification worker image.
  - Current usage is `./push-image.sh oolshik-notification-worker`.

- `stt-worker/push-image.sh`
  - Builds and pushes STT worker image.
  - Current usage is `./push-image.sh oolshik-stt-worker [cpu|gpu]`.
  - Tags include compute suffixes such as `v3-cpu`, `latest-cpu`, `v3-gpu`, `latest-gpu`.

## Important Findings

- Local full backend is already supported by `docker-compose.yml` using the `full` profile.
- API, database, Kafka, STT worker, and notification worker are separate Compose services.
- STT worker is also a full EC2-managed service, not only a local Compose service.
- Mobile app currently lives outside this repo at `/Users/nitinkalokhe/Ni3/Oolshik`, but the runner must not hardcode that path as the only option.
- Mobile local testing must explicitly pass `EXPO_PUBLIC_API_URL`; current mobile config defaults to `https://www.oolshik.in` when empty.
- EC2 API script currently runs the container with `SPRING_PROFILES_ACTIVE=local`, while ASG user-data uses `SPRING_PROFILES_ACTIVE=prod`. This is a Phase 1 prerequisite, not a later cleanup item.
- Existing `.env`-style files include real-looking secrets. The new script usage must point EC2 to `/etc/.../env` and AWS Secrets Manager, not repo env files.
- Notification worker and STT worker both default to host port `8081` on EC2. They cannot run together on the same EC2 host without changing ports.
- API and notification worker ASG scripts assume Amazon Linux 2023 with `dnf`; STT worker user-data assumes Ubuntu with `apt-get`.
- Health endpoints differ by service: API and notification worker use `/actuator/health`; STT worker uses `/health`.

## Phase 1 Prerequisites

These must be settled before shipping EC2 support in `run-all.sh`:

- Fix API EC2 profile behavior. `scripts/run-ec2.sh` must not start an EC2 API container with `SPRING_PROFILES_ACTIVE=local`. Use `prod` for production EC2 or make the target profile explicit through a controlled env value.
- Decide EC2 execution context:
  - Host-local mode: copy `run-all.sh` to the EC2 host and run it on the host.
  - SSH mode: run `run-all.sh` on the developer machine and have it SSH into EC2 to invoke the host scripts.
- For the first implementation, prefer host-local mode because existing `scripts/run-ec2.sh`, `notification-worker/scripts/run-ec2.sh`, and `stt-worker/scripts/run-ec2.sh` are designed to run on their EC2 hosts.
- Make the mobile directory configurable through `MOBILE_DIR` and/or `--mobile-dir`.
- Do not add `--allow-repo-env`. EC2 should hard-fail if `/etc/.../env` is missing.

## Proposed Script

Add:

```text
scripts/run-all.sh
```

Command model:

```bash
./scripts/run-all.sh <target> <action> <service...> [options]
```

Targets:

```text
local
ec2
aws
```

Actions:

```text
up
deploy
build
start
stop
restart
status
logs
push
help
```

Services:

```text
api
db
kafka
stt-worker
notification-worker
mobile
all
```

## Built-In Usage Requirements

The script must include a `usage()` function.

Print usage when:

- No args are passed.
- `help`, `-h`, or `--help` is passed.
- Target is unknown.
- Action is unknown.
- Service is unknown.
- Unsupported target/action/service combination is requested.

Usage text should include:

```bash
Usage:
  ./scripts/run-all.sh <target> <action> <service...> [options]

Targets:
  local   Run services on this machine using Docker Compose and Expo.
  ec2     Run or deploy services on an EC2 host using Docker/ECR.
  aws     AWS helper actions such as pushing images to ECR.

Actions:
  up       Local alias for start.
  deploy   EC2 deploy/start from ECR image.
  build    Local build helper for supported services.
  start    Start service.
  stop     Stop service.
  restart  Restart service.
  status   Show service status and health.
  logs     Follow service logs.
  push     Build and push image to ECR.

Services:
  api
  db
  kafka
  stt-worker
  notification-worker
  mobile
  all

Examples:
  ./scripts/run-all.sh local up all
  ./scripts/run-all.sh local up api
  ./scripts/run-all.sh local up kafka notification-worker
  ./scripts/run-all.sh local build stt-worker
  ./scripts/run-all.sh local build stt-worker --gpu
  ./scripts/run-all.sh local logs api
  ./scripts/run-all.sh local up mobile --platform ios
  ./scripts/run-all.sh local up mobile --platform android
  ./scripts/run-all.sh local up mobile --api-url http://192.168.29.209:8080
  MOBILE_DIR=/Users/me/projects/Oolshik ./scripts/run-all.sh local up mobile --platform ios
  ./scripts/run-all.sh local up mobile --mobile-dir /Users/me/projects/Oolshik --platform ios

  ./scripts/run-all.sh ec2 deploy api
  ./scripts/run-all.sh ec2 deploy notification-worker
  ./scripts/run-all.sh ec2 deploy stt-worker
  ./scripts/run-all.sh ec2 restart api
  ./scripts/run-all.sh ec2 status all
  ./scripts/run-all.sh ec2 logs notification-worker

  ./scripts/run-all.sh aws push api
  ./scripts/run-all.sh aws push notification-worker
  ./scripts/run-all.sh aws push stt-worker
  ./scripts/run-all.sh aws push stt-worker --gpu
```

## Local Behavior

Local target should use Docker Compose.

Service mappings:

```text
db                   docker compose up -d db
api                  docker compose up -d --build db api
kafka                docker compose --profile full up -d kafka
stt-worker           docker compose --profile full up -d --build kafka stt-worker
notification-worker  docker compose --profile full up -d --build db kafka notification-worker
all                  docker compose --profile full up -d --build
mobile               start Expo from MOBILE_DIR, defaulting to /Users/nitinkalokhe/Ni3/Oolshik only on this machine
```

STT worker local build:

```text
stt-worker local CPU build  stt-worker/scripts/build.sh cpu
stt-worker local GPU build  stt-worker/scripts/build.sh gpu
```

Local build action:

```text
local build stt-worker        stt-worker/scripts/build.sh cpu
local build stt-worker --gpu  stt-worker/scripts/build.sh gpu
```

The runner should delegate to `stt-worker/scripts/build.sh` instead of reimplementing Docker build logic.

STT startup note:

- First STT worker start may take several minutes while model files are downloaded or initialized.
- EC2 `stt-worker/scripts/run-ec2.sh` allows up to 15 minutes for `/health`.
- Local commands should print a clear note before starting STT worker so the wait does not look like a hang.

Local dependency rules:

```text
api needs db
stt-worker needs kafka
notification-worker needs db + kafka
mobile needs api reachable
```

The runner should start local dependencies automatically and print what it is doing.

Examples:

```bash
./scripts/run-all.sh local up api
./scripts/run-all.sh local up kafka notification-worker
./scripts/run-all.sh local restart api
./scripts/run-all.sh local logs notification-worker
./scripts/run-all.sh local status all
```

For mobile:

```bash
./scripts/run-all.sh local up mobile --platform ios
./scripts/run-all.sh local up mobile --platform android
./scripts/run-all.sh local up mobile --api-url http://192.168.29.209:8080
```

Mobile rules:

- `mobile` is local-only.
- Default iOS simulator API URL should be `http://localhost:8080`.
- Default Android emulator API URL should be `http://10.0.2.2:8080`.
- Physical phone should require explicit `--api-url`.
- The script must export `EXPO_PUBLIC_API_URL`.
- The script should also export `EXPO_PUBLIC_AUTH_PHONE_OTP_ENABLED=true` for local OTP testing unless overridden.
- The script must support `MOBILE_DIR=/path/to/Oolshik`.
- The script should support `--mobile-dir /path/to/Oolshik`.
- If both are set, `--mobile-dir` takes precedence over `MOBILE_DIR`.
- If `MOBILE_DIR` does not exist or does not contain `package.json`, fail with a clear message.

## EC2 Behavior

EC2 target should use Docker and ECR from the EC2 host.

Phase 1 execution model:

```text
run-all.sh is copied to, or checked out on, the EC2 host and run there.
```

This matches the existing service scripts. They expect local access to:

```text
Docker daemon
AWS CLI credential chain / instance role
/etc/oolshik-backend/env
/etc/notification-worker/env
/etc/stt-worker/env
/etc/oolshik-backend/firebase-sa.json
/opt/stt-worker/models
/opt/stt-worker/tmp
```

Developer-machine SSH orchestration is a separate future mode. Do not imply that `./scripts/run-all.sh ec2 deploy api` from a laptop will deploy to EC2 unless SSH support is explicitly implemented.

Phase 1 EC2 image prerequisite:

- `ec2 deploy ...` pulls images from ECR.
- Phase 1 assumes images already exist in ECR.
- Push images separately with the existing push scripts until `aws push ...` is wired into `run-all.sh` in Phase 2:
  - `./push-image.sh oolshik-api`
  - `notification-worker/push-image.sh oolshik-notification-worker`
  - `stt-worker/push-image.sh oolshik-stt-worker cpu`
  - `stt-worker/push-image.sh oolshik-stt-worker gpu`

Initial implementation should delegate:

```text
ec2 deploy api                  scripts/run-ec2.sh start
ec2 restart api                 scripts/run-ec2.sh restart
ec2 stop api                    scripts/run-ec2.sh stop
ec2 status api                  scripts/run-ec2.sh status
ec2 logs api                    scripts/run-ec2.sh logs

ec2 deploy notification-worker  notification-worker/scripts/run-ec2.sh start
ec2 restart notification-worker notification-worker/scripts/run-ec2.sh restart
ec2 stop notification-worker    notification-worker/scripts/run-ec2.sh stop
ec2 status notification-worker  notification-worker/scripts/run-ec2.sh status
ec2 logs notification-worker    notification-worker/scripts/run-ec2.sh logs

ec2 deploy stt-worker           stt-worker/scripts/run-ec2.sh start
ec2 restart stt-worker          stt-worker/scripts/run-ec2.sh restart
ec2 stop stt-worker             stt-worker/scripts/run-ec2.sh stop
ec2 status stt-worker           stt-worker/scripts/run-ec2.sh status
ec2 logs stt-worker             stt-worker/scripts/run-ec2.sh logs
```

EC2 service registry:

```text
api
notification-worker
stt-worker
```

Phase 1 EC2 `all` must not include Kafka. Kafka hosting is undecided, so Kafka must be explicitly skipped with a clear note.

Phase 1 EC2 `all` also needs host-role awareness:

- For Phase 1, do not require a host-role config file.
- `ec2 status all` should inspect Docker and report only known service containers that exist or are running on the current host:
  - `oolshik-api`
  - `notification-worker`
  - `stt-worker`
- `ec2 logs all`, `ec2 restart all`, and `ec2 stop all` should apply only to known service containers present on the current host.
- `ec2 deploy all` should not blindly deploy every service. It should fail with a clear message asking the user to name the service explicitly, unless a future host-role mechanism is added.
- A single host should not run both `notification-worker` and `stt-worker` with current defaults because both bind host port `8081`.
- If a future host intentionally runs multiple services, port defaults must be reconciled first.

Do not include `mobile` in EC2.

EC2 dependency rules:

```text
api needs DB reachable
api needs Kafka if APP_MESSAGING_KAFKA_ENABLED=true
notification-worker needs DB + Kafka
stt-worker needs Kafka and S3 access for audio fetched from S3
```

The runner should not blindly start unavailable dependencies on EC2. It should health-check or print a clear warning.

EC2 health checks must be service-specific:

```text
api                  http://localhost:8080/actuator/health
notification-worker  http://localhost:8081/actuator/health
stt-worker           http://localhost:8081/health
stt-worker metrics   http://localhost:9108/metrics
```

## Kafka Decision Needed

Before implementing EC2 Kafka commands, choose one AWS design:

```text
Option A: Kafka Docker container on the same EC2 host.
Option B: Kafka Docker container on a dedicated private EC2 host.
Option C: AWS MSK later.
```

Until this is decided, `ec2 deploy kafka` should either be unsupported or clearly marked experimental.

For Phase 1, `ec2 deploy kafka`, `ec2 restart kafka`, `ec2 status kafka`, and `ec2 logs kafka` should be unsupported and should print:

```text
EC2 Kafka management is not implemented yet. Decide Kafka hosting first: same-host Docker, dedicated EC2, or MSK.
```

## AWS Image Push Behavior

The `aws push` target can delegate to the existing push-image scripts.

Mappings:

```text
aws push api                  ./push-image.sh oolshik-api
aws push notification-worker  notification-worker/push-image.sh oolshik-notification-worker
aws push stt-worker           stt-worker/push-image.sh oolshik-stt-worker cpu
aws push stt-worker --gpu     stt-worker/push-image.sh oolshik-stt-worker gpu
```

Push behavior:

- Root `push-image.sh` and `notification-worker/push-image.sh` auto-increment version tags by querying ECR for existing `v<N>` tags.
- Each push creates a new versioned tag and also updates `latest`.
- `aws push api` is not idempotent; it creates a new version tag such as `v4` on each successful push.
- `aws push notification-worker` behaves the same way with tags such as `v4` and `latest`.
- `stt-worker/push-image.sh` auto-increments per compute variant using `v<N>-cpu` / `latest-cpu` or `v<N>-gpu` / `latest-gpu`.
- `aws push stt-worker` should default to CPU.
- `aws push stt-worker --gpu` should push the GPU variant.

Future Phase 2 addition:

```bash
./scripts/run-all.sh aws redeploy api
./scripts/run-all.sh aws redeploy notification-worker
./scripts/run-all.sh aws redeploy stt-worker
```

This should perform build, push, then deploy. It requires a clear EC2 execution model first:

- In host-local mode, `redeploy` is not enough by itself because image building usually happens on the developer machine or CI.
- In SSH mode, `redeploy` can push from the developer machine and then SSH into EC2 to run the deploy command.
- In CI/CD mode, build/push/deploy should be handled by the pipeline rather than a laptop script.

## Environment Layout

Local:

```text
.env
```

Mobile local:

```text
${MOBILE_DIR}/.env.local
```

EC2 API:

```text
/etc/oolshik-backend/env
/etc/oolshik-backend/firebase-sa.json
```

EC2 notification worker:

```text
/etc/notification-worker/env
/etc/oolshik-backend/firebase-sa.json
```

EC2 STT worker:

```text
/etc/stt-worker/env
/opt/stt-worker/models
/opt/stt-worker/tmp
```

AWS Secrets Manager:

```text
oolshik/dev/backend/env
oolshik/dev/notification-worker/env
oolshik/dev/firebase-admin-sdk-json
```

STT worker user-data currently writes `/etc/stt-worker/env` directly. If STT env should also come from Secrets Manager, add a dedicated secret such as `oolshik/dev/stt-worker/env` and update `stt-worker/scripts/user-data.sh` before wiring it into the runner.

The runner should never require real secrets in repo files.

For EC2, missing `/etc/.../env` should be a hard failure. The runner should not fall back to repo env files.

## Cleanup And Consistency Tasks

Before Phase 1 implementation:

- Make EC2 API profile consistent: use `prod` for production EC2, or explicitly support `cloud-dev`.
- Remove or disable repo env fallback for EC2 paths.
- Define EC2 execution context as host-local for Phase 1.
- Make API and notification worker scripts share the same Docker access behavior. API supports `sudo docker`; notification worker currently assumes direct `docker`.
- Decide whether STT worker should also support the same Docker access fallback behavior.
- Do not assume one Linux distribution for all EC2 services:
  - API ASG user-data uses `dnf` for Amazon Linux 2023.
  - Notification worker ASG user-data uses `dnf` for Amazon Linux 2023.
  - STT worker user-data uses `apt-get` for Ubuntu.
- Decide whether to standardize host OS by service role, or keep service-specific user-data.
- Reconcile host port `8081` before running notification worker and STT worker on the same EC2 host.
- Remove hardcoded Kafka private IP defaults such as `10.20.0.13:9092` from EC2 scripts, or require `KAFKA_BOOTSTRAP_SERVERS` explicitly for EC2. Current defaults can silently point a new environment at the wrong broker.

During or after Phase 1:

- Add richer `usage()` text to existing service scripts too, or make `run-all.sh` the documented entry point.
- Sanitize real-looking credentials from committed env files and move real values to AWS Secrets Manager.

## Implementation Phases

### Phase 1: Thin Orchestrator

- Fix EC2 API profile behavior first.
- Define Phase 1 EC2 execution as host-local.
- Add `scripts/run-all.sh`.
- Implement usage/help.
- Implement local Docker Compose commands.
- Implement `local build stt-worker` and `local build stt-worker --gpu`.
- Implement mobile local startup with configurable `MOBILE_DIR` / `--mobile-dir`.
- Delegate EC2 API commands to `scripts/run-ec2.sh`.
- Delegate EC2 notification worker commands to `notification-worker/scripts/run-ec2.sh`.
- Delegate EC2 STT worker commands to `stt-worker/scripts/run-ec2.sh`.
- Define `ec2 all` by host role, not as a blind global service list.
- For Phase 1, implement `ec2 status/logs/restart/stop all` by inspecting known containers on the current host; make `ec2 deploy all` fail until a host-role mechanism exists.
- Make EC2 Kafka commands explicitly unsupported until Kafka hosting is decided.
- Use per-service health endpoint definitions.

### Phase 2: AWS Image Push

- Add `aws push api`.
- Add `aws push notification-worker`.
- Add `aws push stt-worker`.
- Add `aws push stt-worker --gpu`.
- Delegate notification worker push to `notification-worker/push-image.sh`.
- Delegate STT worker push to `stt-worker/push-image.sh`.
- Document that each push creates a new version tag and updates the matching latest tag.
- Add a future combined `redeploy` flow only after SSH/CI deployment behavior is decided.

### Phase 3: EC2 Kafka

- Decide Kafka hosting design.
- Add `ec2 deploy kafka`, `ec2 restart kafka`, `ec2 status kafka`, and `ec2 logs kafka` only after the design is fixed.

### Phase 4: Script Consolidation

- Reduce duplicated Docker/ECR logic in service scripts.
- Standardize env loading.
- Standardize health checks.
- Standardize log/status output.

## Recommended First Implementation

Start with:

```bash
./scripts/run-all.sh local up all
./scripts/run-all.sh local up api
./scripts/run-all.sh local up mobile --platform ios
MOBILE_DIR=/Users/me/projects/Oolshik ./scripts/run-all.sh local up mobile --platform ios
./scripts/run-all.sh ec2 deploy api
./scripts/run-all.sh ec2 deploy notification-worker
./scripts/run-all.sh ec2 deploy stt-worker
./scripts/run-all.sh ec2 status all
```

Defer:

```bash
./scripts/run-all.sh ec2 deploy kafka
./scripts/run-all.sh aws redeploy api
```

until Kafka hosting and SSH/CI deployment behavior are finalized.

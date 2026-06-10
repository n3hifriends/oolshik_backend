#!/usr/bin/env bash
# Push stt-worker image to ECR.
# Builds a cpu or gpu variant, tags it with a compute suffix, and pushes both
# a versioned tag and a rolling latest tag.
#
# Usage:
#   ./push-image.sh <ecr-repo-name> [cpu|gpu]
#
# Examples:
#   ./push-image.sh oolshik-stt-worker          # cpu variant (default)
#   ./push-image.sh oolshik-stt-worker gpu       # gpu variant
#
# Optional env overrides:
#   AWS_REGION=ap-south-1
#   ECR_REGISTRY=<account>.dkr.ecr.<region>.amazonaws.com
#   IMAGE_PLATFORM=linux/amd64
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-south-1}"
ECR_REGISTRY="${ECR_REGISTRY:-653895707563.dkr.ecr.${AWS_REGION}.amazonaws.com}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"

usage() {
  cat <<'EOF'
Usage:
  ./push-image.sh <ecr-repo-name> [cpu|gpu]

Examples:
  ./push-image.sh oolshik-stt-worker
  ./push-image.sh oolshik-stt-worker gpu

Tags pushed:
  <registry>/<repo>:v<N>-cpu   (or -gpu)
  <registry>/<repo>:latest-cpu (or -gpu)

Optional env overrides:
  AWS_REGION=ap-south-1
  ECR_REGISTRY=<account>.dkr.ecr.<region>.amazonaws.com
  IMAGE_PLATFORM=linux/amd64
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1" >&2
    exit 1
  fi
}

ensure_repo() {
  local repo_name="$1"
  if aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo_name" >/dev/null 2>&1; then
    return 0
  fi
  echo "ECR repository '$repo_name' not found — creating..."
  aws ecr create-repository --region "$AWS_REGION" --repository-name "$repo_name" >/dev/null
}

next_version_tag() {
  local repo_name="$1"
  local compute="$2"
  local tags max=0 tag
  tags="$(aws ecr list-images \
    --region "$AWS_REGION" \
    --repository-name "$repo_name" \
    --filter tagStatus=TAGGED \
    --query 'imageIds[*].imageTag' \
    --output text 2>/dev/null || true)"

  # Only count tags matching the current compute suffix (v<N>-cpu or v<N>-gpu)
  for tag in $tags; do
    if [[ "$tag" =~ ^v([0-9]+)-${compute}$ ]]; then
      if (( BASH_REMATCH[1] > max )); then
        max="${BASH_REMATCH[1]}"
      fi
    fi
  done
  echo "v$((max + 1))-${compute}"
}

build_and_push() {
  local compute="$1"
  local remote_version_tag="$2"
  local remote_latest_tag="$3"

  if docker buildx version >/dev/null 2>&1; then
    docker buildx build \
      --platform "$IMAGE_PLATFORM" \
      --build-arg COMPUTE="${compute}" \
      --provenance=false \
      --sbom=false \
      -t "${remote_version_tag}" \
      -t "${remote_latest_tag}" \
      --push \
      .
    return 0
  fi

  # Fallback: plain docker build + push
  local local_tag="${4:-stt-worker}:${remote_version_tag##*:}"
  docker build \
    --build-arg COMPUTE="${compute}" \
    -t "${local_tag}" \
    .
  docker tag "${local_tag}" "${remote_version_tag}"
  docker tag "${local_tag}" "${remote_latest_tag}"
  docker push "${remote_version_tag}"
  docker push "${remote_latest_tag}"
}

main() {
  if [[ $# -lt 1 || $# -gt 2 ]]; then
    usage
    exit 1
  fi

  require_cmd aws
  require_cmd docker

  local repo_name="$1"
  local compute="${2:-cpu}"

  if [[ "$compute" != "cpu" && "$compute" != "gpu" ]]; then
    echo "ERROR: compute must be 'cpu' or 'gpu', got: $compute" >&2
    exit 1
  fi

  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  cd "$script_dir"

  ensure_repo "$repo_name"

  echo "Logging in to ECR registry ${ECR_REGISTRY}..."
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"

  local version_tag
  version_tag="$(next_version_tag "$repo_name" "$compute")"

  local remote_version_tag="${ECR_REGISTRY}/${repo_name}:${version_tag}"
  local remote_latest_tag="${ECR_REGISTRY}/${repo_name}:latest-${compute}"

  echo ""
  echo "Building stt-worker image"
  echo "  Compute variant : ${compute}"
  echo "  Platform        : ${IMAGE_PLATFORM}"
  echo "  Version tag     : ${remote_version_tag}"
  echo "  Latest tag      : ${remote_latest_tag}"
  echo ""

  build_and_push "$compute" "$remote_version_tag" "$remote_latest_tag" "$repo_name"

  cat <<EOF

Done.
Compute  : ${compute}
Version  : ${version_tag}
Pushed   :
  ${remote_version_tag}
  ${remote_latest_tag}

To deploy on EC2 run:
  IMAGE_URI=${remote_version_tag} COMPUTE=${compute} ./scripts/run-ec2.sh
EOF
}

main "$@"

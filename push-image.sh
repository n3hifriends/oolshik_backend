#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-south-1}"
ECR_REGISTRY="${ECR_REGISTRY:-653895707563.dkr.ecr.${AWS_REGION}.amazonaws.com}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"

usage() {
  cat <<'EOF'
Usage:
  ./push-image.sh <image-name>

Example:
  ./push-image.sh oolshik-api

Optional environment overrides:
  AWS_REGION=ap-south-1
  ECR_REGISTRY=653895707563.dkr.ecr.ap-south-1.amazonaws.com
  IMAGE_PLATFORM=linux/amd64
EOF
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

ensure_repo() {
  local repo_name="$1"
  if aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo_name" >/dev/null 2>&1; then
    return 0
  fi
  echo "ECR repository '$repo_name' not found. Creating it..."
  aws ecr create-repository --region "$AWS_REGION" --repository-name "$repo_name" >/dev/null
}

next_version_tag() {
  local repo_name="$1"
  local tags
  local max=0
  local tag
  tags="$(aws ecr list-images \
    --region "$AWS_REGION" \
    --repository-name "$repo_name" \
    --filter tagStatus=TAGGED \
    --query 'imageIds[*].imageTag' \
    --output text 2>/dev/null || true)"

  for tag in $tags; do
    if [[ "$tag" =~ ^v([0-9]+)$ ]]; then
      if (( BASH_REMATCH[1] > max )); then
        max="${BASH_REMATCH[1]}"
      fi
    fi
  done

  echo "v$((max + 1))"
}

docker_build_and_push() {
  local remote_version_tag="$1"
  local remote_latest_tag="$2"
  if docker buildx version >/dev/null 2>&1; then
    docker buildx build \
      --platform "$IMAGE_PLATFORM" \
      --provenance=false \
      -t "${remote_version_tag}" \
      -t "${remote_latest_tag}" \
      --push \
      .
    return 0
  fi

  local repo_name="$3"
  local version_tag="$4"
  local local_version_tag="${repo_name}:${version_tag}"
  local local_latest_tag="${repo_name}:latest"

  docker build -t "${local_version_tag}" .
  docker tag "$local_version_tag" "$local_latest_tag"
  docker tag "$local_version_tag" "$remote_version_tag"
  docker tag "$local_version_tag" "$remote_latest_tag"
  docker push "$remote_version_tag"
  docker push "$remote_latest_tag"
}

main() {
  if [[ $# -ne 1 ]]; then
    usage
    exit 1
  fi

  require_cmd aws
  require_cmd docker

  local repo_name="$1"
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  cd "$script_dir"

  ensure_repo "$repo_name"

  echo "Logging in to ECR registry ${ECR_REGISTRY}..."
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

  local version_tag
  version_tag="$(next_version_tag "$repo_name")"

  local remote_version_tag="${ECR_REGISTRY}/${repo_name}:${version_tag}"
  local remote_latest_tag="${ECR_REGISTRY}/${repo_name}:latest"

  echo "Building backend image for platform ${IMAGE_PLATFORM}..."
  echo "Publishing image as:"
  echo "  ${remote_version_tag}"
  echo "  ${remote_latest_tag}"
  docker_build_and_push "$remote_version_tag" "$remote_latest_tag" "$repo_name" "$version_tag"

  cat <<EOF
Done.
Version tag: ${version_tag}
Latest tag: latest
Backend image URI:
  ${remote_version_tag}
  ${remote_latest_tag}
EOF
}

main "$@"

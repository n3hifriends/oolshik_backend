#!/usr/bin/env bash
# Build the stt-worker Docker image locally.
# Works on macOS (Apple Silicon or Intel) and Linux.
#
# Usage:
#   ./scripts/build.sh [cpu|gpu]
#
# Examples:
#   ./scripts/build.sh          # cpu (default)
#   ./scripts/build.sh gpu      # gpu / CUDA 12.1
#
# The resulting local image is tagged:
#   oolshik-stt-worker:local-<compute>
#   oolshik-stt-worker:local          (alias for the build above)
set -euo pipefail

COMPUTE="${1:-cpu}"
IMAGE_NAME="${IMAGE_NAME:-oolshik-stt-worker}"

if [[ "$COMPUTE" != "cpu" && "$COMPUTE" != "gpu" ]]; then
  echo "ERROR: argument must be 'cpu' or 'gpu', got: $COMPUTE" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."

VERSIONED_TAG="${IMAGE_NAME}:local-${COMPUTE}"
LATEST_TAG="${IMAGE_NAME}:local"

echo "========================================"
echo "  Building stt-worker"
echo "  Compute variant : ${COMPUTE}"
echo "  Tags            : ${VERSIONED_TAG}, ${LATEST_TAG}"
echo "========================================"
echo ""

docker build \
  --build-arg COMPUTE="${COMPUTE}" \
  -t "${VERSIONED_TAG}" \
  -t "${LATEST_TAG}" \
  .

echo ""
echo "Build complete."
echo ""
echo "To run locally (CPU):"
echo "  docker-compose up"
echo ""
echo "To run locally (GPU):"
echo "  docker-compose -f docker-compose.yml -f docker-compose.gpu.yml up"
echo ""
echo "Image size:"
docker images "${IMAGE_NAME}" --format "  {{.Tag}}\t{{.Size}}"

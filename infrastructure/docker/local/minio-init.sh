#!/bin/sh
# Creates the private local documents bucket. Idempotent.
set -eu
ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
BUCKET="${MINIO_BUCKET:-kyro-documents-local}"
i=0
until mc alias set local "$ENDPOINT" "${MINIO_ROOT_USER:-kyro-minio}" "${MINIO_ROOT_PASSWORD:-kyro-minio-secret}"; do
  i=$((i + 1))
  if [ "$i" -ge 30 ]; then echo "minio-init: MinIO not reachable at $ENDPOINT" >&2; exit 1; fi
  sleep 2
done
mc mb --ignore-existing "local/${BUCKET}"
mc anonymous set none "local/${BUCKET}"
echo "minio-init: bucket ${BUCKET} ready (anonymous access: $(mc anonymous get "local/${BUCKET}"))"

#!/usr/bin/env bash
set -euo pipefail

# Defaults (can be overridden by env vars)
POSTGRES_HOST=${POSTGRES_HOST:-postgres}
POSTGRES_PORT=${POSTGRES_PORT:-5432}

# Wait for Postgres TCP port to be available
echo "Waiting for Postgres at ${POSTGRES_HOST}:${POSTGRES_PORT}..."
if command -v pg_isready >/dev/null 2>&1; then
  echo "Using pg_isready to wait for Postgres availability"
  until pg_isready -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" >/dev/null 2>&1; do
    echo "Postgres is not available yet - sleeping 1s"
    sleep 1
  done
else
  echo "pg_isready not available, falling back to TCP connect check"
  while ! bash -c "</dev/tcp/${POSTGRES_HOST}/${POSTGRES_PORT}" >/dev/null 2>&1; do
    echo "Postgres is not available yet - sleeping 1s"
    sleep 1
  done
fi

echo "Postgres reachable, starting application"
exec java -jar app.jar


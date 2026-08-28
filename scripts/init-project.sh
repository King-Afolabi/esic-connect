#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p \
  backend \
  frontend \
  ai-service/app \
  ai-service/tests \
  iot-device/src \
  iot-device/tests \
  infrastructure/mosquitto/config \
  infrastructure/mysql/init \
  infrastructure/nginx \
  data/uploads/justifications \
  data/uploads/claims \
  samples \
  report/images \
  presentation/images \
  docs/adr

touch \
  .env.example \
  .gitignore \
  compose.yaml \
  README.md \
  ai-service/requirements.txt \
  iot-device/requirements.txt \
  samples/students-template.csv \
  samples/schedule-template.csv

echo "Arborescence initialisée dans $ROOT"
echo "Étape suivante : créer compose.yaml puis initialiser Spring Boot."
#!/usr/bin/env bash
# Update the running backend on the server to the latest code on GitHub.
# Usage (on the server):  /opt/foreverdentalcare/backend/deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Pulling latest code"
git pull --ff-only origin main

echo "==> Building image"
sudo docker compose -f docker-compose.prod.yml build backend

echo "==> Restarting containers"
sudo docker compose -f docker-compose.prod.yml up -d

echo "==> Waiting for health check"
for i in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/actuator/health)" = "200" ]; then
    echo "Backend is healthy."
    sudo docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done
echo "Backend did not become healthy. Recent logs:" >&2
sudo docker logs --tail 50 dental-clinic-backend >&2
exit 1

#!/usr/bin/env bash
# Update the running backend on the server to the latest code on GitHub.
# Usage (on the server):  /opt/foreverdentalcare/backend/deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Pulling latest code"
git pull --ff-only origin main

NGINX_SITE=/etc/nginx/sites-available/foreverdentalcare-backend
if ! sudo cmp -s deploy/nginx/foreverdentalcare-backend.conf "$NGINX_SITE"; then
  echo "==> Updating Nginx config"
  sudo cp "$NGINX_SITE" "$NGINX_SITE.bak" 2>/dev/null || true
  sudo install -m 644 -o root -g root deploy/nginx/foreverdentalcare-backend.conf "$NGINX_SITE"
  if sudo nginx -t; then
    sudo systemctl reload nginx
  else
    echo "Nginx config test failed; restoring the previous config." >&2
    sudo cp "$NGINX_SITE.bak" "$NGINX_SITE"
    exit 1
  fi
fi

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

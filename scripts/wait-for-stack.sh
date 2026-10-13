#!/usr/bin/env bash
# Waits until the gateway can route to all three services (Eureka registration takes a few seconds).
BASE=${BASE_URL:-http://localhost:8080}
for i in $(seq 1 80); do
  token=$(curl -sf "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d '{"email":"admin@buchhaus.local","password":"Admin@12345"}' 2>/dev/null \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])" 2>/dev/null)
  if [ -n "$token" ] && curl -sf "$BASE/api/books" > /dev/null \
     && curl -sf "$BASE/api/orders" -H "Authorization: Bearer $token" > /dev/null; then
    echo "Stack ready: gateway routes to user-, catalog- and order-service"
    exit 0
  fi
  sleep 3
done
echo "Stack did not become ready"; docker compose ps; exit 1

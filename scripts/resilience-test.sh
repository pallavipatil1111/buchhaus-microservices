#!/usr/bin/env bash
# Stops the catalog-service in the running Docker Compose stack and checks that the rest degrades
# gracefully, then starts it again and checks that everything recovers on its own.
set -euo pipefail
BASE=${BASE_URL:-http://localhost:8080}
pass() { echo "  ok - $1"; }
fail() { echo "  FAIL - $1"; exit 1; }
json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

admin=$(curl -sf "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@buchhaus.local","password":"Admin@12345"}' | json "['token']")
email="chaos-$(date +%s)@example.com"
token=$(curl -sf "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Chaos\",\"email\":\"$email\",\"password\":\"secret123\"}" | json "['token']")
curl -sf -X PUT "$BASE/api/cart/items/1" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d '{"quantity":1}' > /dev/null
address='{"name":"Chaos","phone":"9876543210","address":"Flat 1","city":"Pune","pincode":"411045"}'

echo "Stopping catalog-service"
docker compose stop catalog-service > /dev/null

code=$(curl -s -o /tmp/checkout.json -w '%{http_code}' "$BASE/api/orders" -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' -d "$address")
[ "$code" = 503 ] && grep -q "Your cart is safe" /tmp/checkout.json && pass "checkout answers 503 with a clear message" \
  || fail "checkout returned $code: $(cat /tmp/checkout.json)"

stats=$(curl -sf "$BASE/api/admin/stats" -H "Authorization: Bearer $admin")
[ "$(echo "$stats" | json "['catalogueAvailable']")" = False ] && pass "admin dashboard still loads, without stock figures" \
  || fail "admin stats: $stats"

orders=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/orders" -H "Authorization: Bearer $token")
[ "$orders" = 200 ] && pass "order history (order-service only) still works" || fail "orders returned $orders"

cart=$(curl -sf "$BASE/api/cart" -H "Authorization: Bearer $token" -o /dev/null -w '%{http_code}' || true)
echo "  (cart view while the catalogue is down: HTTP $cart)"

echo "Starting catalog-service again"
docker compose start catalog-service > /dev/null
for i in $(seq 1 60); do
  code=$(curl -s -o /tmp/checkout.json -w '%{http_code}' "$BASE/api/orders" -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d "$address")
  [ "$code" = 201 ] && break
  sleep 3
done
[ "$code" = 201 ] && pass "checkout works again once the catalogue is back (no restart of other services)" \
  || fail "checkout after recovery returned $code: $(cat /tmp/checkout.json)"
echo "Resilience test passed"

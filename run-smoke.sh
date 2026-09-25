#!/usr/bin/env bash
set -e

PORT="${1:-19090}"
BASE_URL="http://127.0.0.1:$((PORT + 1))/api"

echo "=== ANVEX Backend Smoke Run ==="

mvn -q package
JAR="target/anvex-1.0-SNAPSHOT.jar"
CP="$JAR:target/lib/*"

RUN_DIR="target/smoke-$(date +%s%N)"
mkdir -p "$RUN_DIR"

java -jar "$JAR" "$PORT" > "$RUN_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
cleanup() { kill "$BACKEND_PID" 2>/dev/null || true; }
trap cleanup EXIT

READY=false
for i in $(seq 1 40); do
  sleep 0.25
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "BACKEND EXITED EARLY"; cat "$RUN_DIR/backend.log" 2>/dev/null || true; exit 1
  fi
  if grep -q "AuthenticationServer started" "$RUN_DIR/backend.log" 2>/dev/null; then
    READY=true; break
  fi
done
if [ "$READY" != "true" ]; then echo "BACKEND DID NOT START"; exit 1; fi

echo "[1/4] Status API..."
STATUS=$(curl -s --max-time 10 "$BASE_URL/status")
echo "$STATUS"
echo "$STATUS" | grep -q "runId" || { echo "FAIL: status API"; exit 1; }

echo "[2/4] Live config update + attack burst..."
curl -s --max-time 10 -X PUT -d '{"highDelayMs":100}' "$BASE_URL/config" > /dev/null
curl -s --max-time 10 -X PUT -d '{"blockCooldownMs":1500}' "$BASE_URL/config" > /dev/null
curl -s --max-time 10 -X PUT -d '{"reaperIntervalMs":200}' "$BASE_URL/config" > /dev/null

ATTACK_OUT=$(java -cp "$CP" com.anvex.tools.SmokeClient 127.0.0.1 "$PORT" "192.168.1.50" 20 100 lab_target wrongpass ATTACKER)
echo "$ATTACK_OUT"
BLOCKED_COUNT=$(echo "$ATTACK_OUT" | grep -oP 'blocked=\K\d+')
if [ -z "$BLOCKED_COUNT" ] || [ "$BLOCKED_COUNT" -lt 1 ]; then echo "FAIL: attack was not blocked"; exit 1; fi

LEGIT_OUT=$(java -cp "$CP" com.anvex.tools.SmokeClient 127.0.0.1 "$PORT" "192.168.1.99" 1 0 lab_target target123 LEGITIMATE)
echo "$LEGIT_OUT"
SUCCESS_COUNT=$(echo "$LEGIT_OUT" | grep -oP 'success=\K\d+')
if [ -z "$SUCCESS_COUNT" ] || [ "$SUCCESS_COUNT" -ne 1 ]; then echo "FAIL: clean source was affected"; exit 1; fi

echo "[3/4] Persisted history via API..."
sleep 3
HISTORY=$(curl -s --max-time 10 "$BASE_URL/sources/192.168.1.50/history")
echo "$HISTORY" | head -c 2000; echo ""
echo "$HISTORY" | grep -q "SOURCE_BLOCKED" || { echo "FAIL: persisted block missing"; exit 1; }
echo "$HISTORY" | grep -q "SECURITY_INCIDENT" || { echo "FAIL: persisted incident missing"; exit 1; }
echo "$HISTORY" | grep -q "SOURCE_RELEASED" || { echo "FAIL: persisted release missing"; exit 1; }

echo "[4/4] SSE stream reachable..."
curl -s --max-time 3 "$BASE_URL/events/stream" | head -c 200 | grep -q "retry" || echo "WARN: SSE stream did not emit retry hint within 3s"

echo ""
echo "=== SMOKE PASS: packaged JAR, HTTP API, per-source block, clean login, persisted history, evidence and release ==="

#!/usr/bin/env bash
set -e

PORT="${1:-9090}"

echo "=== ANVEX Backend (headless) ==="
cd "$(dirname "$0")"
if [ ! -f target/anvex-1.0-SNAPSHOT.jar ]; then echo "Backend JAR missing. Run mvn package first."; exit 1; fi
echo "Starting ANVEX backend on port $PORT (Ctrl+C to stop)..."
java -jar target/anvex-1.0-SNAPSHOT.jar "$PORT"

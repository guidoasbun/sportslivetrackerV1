#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run-live.sh — Launch the full stack locally with LIVE API-Sports data
#
# Usage:
#   export API_SPORTS_KEY="your-api-sports-key"
#   ./run-live.sh
#
# What it starts:
#   1. Producer  (port 8081) — polls API-Sports, publishes to Kinesis
#   2. API       (port 8080) — serves fixtures + SSE stream to frontend
#   3. Frontend  (port 3000) — Next.js dev server
#
# Press Ctrl+C to stop all services.
# ─────────────────────────────────────────────────────────────────────────────

set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ─── Validate API key ────────────────────────────────────────────────────────
if [ -z "$API_SPORTS_KEY" ]; then
  echo "╔══════════════════════════════════════════════════════════════════╗"
  echo "║  ERROR: API_SPORTS_KEY is not set.                             ║"
  echo "║                                                                ║"
  echo "║  Get your key from: https://dashboard.api-football.com         ║"
  echo "║                                                                ║"
  echo "║  Usage:                                                        ║"
  echo "║    export API_SPORTS_KEY=\"your-key-here\"                       ║"
  echo "║    ./run-live.sh                                               ║"
  echo "╚══════════════════════════════════════════════════════════════════╝"
  exit 1
fi

# ─── Cleanup on exit ──────────────────────────────────────────────────────────
cleanup() {
  echo ""
  echo "🛑 Shutting down all services..."
  # Kill the entire process group (catches Maven child processes, sed pipes, npm, etc.)
  kill -- -$$ 2>/dev/null
  # Fallback: kill any remaining children
  pkill -P $$ 2>/dev/null
  echo "✅ All services stopped."
  exit 0
}
trap cleanup INT TERM

# ─── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ─── Helper: wait for a service to be healthy ────────────────────────────────
wait_for_port() {
  local port=$1
  local name=$2
  local max_wait=60
  local elapsed=0
  while ! curl -sf "http://localhost:$port/actuator/health" >/dev/null 2>&1; do
    sleep 1
    elapsed=$((elapsed + 1))
    if [ $elapsed -ge $max_wait ]; then
      echo -e "${RED}✗ $name failed to start within ${max_wait}s. Check logs above.${NC}"
      return 1
    fi
  done
  echo -e "${GREEN}✓ $name is ready (port $port)${NC}"
}

# ─── 1. Start API (port 8080) — start first since Producer depends on it ─────
echo -e "${BLUE}▶ Starting API (port 8080) — fixtures + SSE stream...${NC}"
cd "$ROOT_DIR"
API_SPORTS_KEY="$API_SPORTS_KEY" \
  MOCK_MODE=false \
  PRODUCER_BASE_URL=http://localhost:8081 \
  ./mvnw spring-boot:run -pl api \
  2>&1 | sed "s/^/[API]      /" &

wait_for_port 8080 "API"

# ─── 2. Start Producer (port 8081) ───────────────────────────────────────────
echo -e "${GREEN}▶ Starting Producer (port 8081) — live API-Sports polling...${NC}"
cd "$ROOT_DIR/producer"
API_SPORTS_KEY="$API_SPORTS_KEY" \
  ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--app.api.sports.mock-mode=false" \
  2>&1 | sed "s/^/[PRODUCER] /" &

wait_for_port 8081 "Producer"

# ─── 3. Start Frontend (port 3000) ───────────────────────────────────────────
echo -e "${YELLOW}▶ Starting Frontend (port 3000) — Next.js dev server...${NC}"
cd "$ROOT_DIR/frontend"
npm run dev 2>&1 | sed "s/^/[FRONTEND] /" &

# ─── Wait ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${NC}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  🏀 All services starting..."
echo -e ""
echo -e "  Frontend:  ${YELLOW}http://localhost:3000${NC}"
echo -e "  API:       ${BLUE}http://localhost:8080/api/fixtures?sport=SOCCER${NC}"
echo -e "  Producer:  ${GREEN}http://localhost:8081/actuator/health${NC}"
echo -e ""
echo -e "  Press ${RED}Ctrl+C${NC} to stop everything."
echo -e "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Wait for all background processes
wait

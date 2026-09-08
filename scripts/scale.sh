#!/usr/bin/env bash
set -euo pipefail

# Manually scale the ECS services up or down, bypassing the scheduled
# scale-to-zero. Useful for demoing outside the normal on-hours window.
#
# Usage:
#   ./scripts/scale.sh up          # bring the app up now (dev)
#   ./scripts/scale.sh down        # take the app down now (dev)
#   ./scripts/scale.sh up prod     # target the prod environment
#   ./scripts/scale.sh status      # show current running/desired counts
#
# NOTE ON THE SCHEDULE:
#   Scheduled scaling still runs on its cron. A manual change holds only
#   until the next scheduled action fires. Example: if you scale "up" at
#   9pm Pacific, the app stays up until the next scheduled scale-down
#   (8pm the following day). If you scale "up" during off-hours and want
#   it to stay up, just remember the schedule will pull it back down at
#   its next scale-down time.

ACTION=${1:-}
ENVIRONMENT=${2:-dev}
AWS_REGION="us-east-1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infrastructure"

if [[ "$ACTION" != "up" && "$ACTION" != "down" && "$ACTION" != "status" ]]; then
  echo "Usage: $0 <up|down|status> [environment]" >&2
  echo "  up      scale all services to 1 task" >&2
  echo "  down    scale all services to 0 tasks" >&2
  echo "  status  show current desired/running counts" >&2
  exit 1
fi

# Resolve cluster + service names from Terraform outputs, with fallbacks
# that match the naming scheme used elsewhere in this repo.
cd "$INFRA_DIR"
CLUSTER=$(terraform output -raw ecs_cluster_name 2>/dev/null || echo "sports-tracker-${ENVIRONMENT}-cluster")
PRODUCER_SERVICE=$(terraform output -raw producer_service_name 2>/dev/null || echo "sports-tracker-${ENVIRONMENT}-producer")
API_SERVICE=$(terraform output -raw api_service_name 2>/dev/null || echo "sports-tracker-${ENVIRONMENT}-api")
FRONTEND_SERVICE=$(terraform output -raw frontend_service_name 2>/dev/null || echo "sports-tracker-${ENVIRONMENT}-frontend")

SERVICES=("$PRODUCER_SERVICE" "$API_SERVICE" "$FRONTEND_SERVICE")

if [[ "$ACTION" == "status" ]]; then
  echo "==> Service status for cluster: $CLUSTER"
  aws ecs describe-services \
    --cluster "$CLUSTER" \
    --services "${SERVICES[@]}" \
    --region "$AWS_REGION" \
    --query 'services[].{Service:serviceName,Desired:desiredCount,Running:runningCount,Pending:pendingCount}' \
    --output table
  exit 0
fi

if [[ "$ACTION" == "up" ]]; then
  COUNT=1
  echo "==> Scaling UP all services to $COUNT task each (cluster: $CLUSTER)"
  echo "    Expect ~1-3 min before the app is reachable (task start + Spring Boot boot)."
else
  COUNT=0
  echo "==> Scaling DOWN all services to $COUNT tasks (cluster: $CLUSTER)"
fi

for SERVICE in "${SERVICES[@]}"; do
  echo "    - $SERVICE -> $COUNT"
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "$SERVICE" \
    --desired-count "$COUNT" \
    --region "$AWS_REGION" > /dev/null
done

echo "==> Done. Check progress with: $0 status $ENVIRONMENT"
if [[ "$ACTION" == "up" ]]; then
  echo "    Reminder: the scheduled scale-down will still pull this back to 0 at its next cron time."
fi

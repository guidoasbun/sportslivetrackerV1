#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/teardown.sh dev
# WARNING: This destroys ALL infrastructure for the given environment.

ENVIRONMENT=${1:-dev}
AWS_REGION="us-east-1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infrastructure"

echo "WARNING: This will destroy all $ENVIRONMENT infrastructure."
read -r -p "Type the environment name to confirm: " CONFIRM

if [ "$CONFIRM" != "$ENVIRONMENT" ]; then
  echo "Aborted."
  exit 1
fi

PROJECT="sportslivetracker"

echo "==> Emptying ECR repositories"
for REPO in "$PROJECT-$ENVIRONMENT-producer" "$PROJECT-$ENVIRONMENT-api" "$PROJECT-$ENVIRONMENT-frontend"; do
  IMAGE_IDS=$(aws ecr list-images \
    --repository-name "$REPO" \
    --region "$AWS_REGION" \
    --query 'imageIds[*]' \
    --output json 2>/dev/null || echo "[]")

  if [ "$IMAGE_IDS" != "[]" ] && [ -n "$IMAGE_IDS" ]; then
    aws ecr batch-delete-image \
      --repository-name "$REPO" \
      --region "$AWS_REGION" \
      --image-ids "$IMAGE_IDS" \
      --output none
    echo "    Cleared $REPO"
  fi
done

echo "==> Emptying S3 artifacts bucket"
BUCKET="$PROJECT-$ENVIRONMENT-lambda-artifacts"
aws s3 rm "s3://$BUCKET" --recursive 2>/dev/null || true

echo "==> Running terraform destroy"
cd "$INFRA_DIR"
terraform destroy \
  -var-file="environments/${ENVIRONMENT}.tfvars" \
  -auto-approve

echo "==> Teardown complete."

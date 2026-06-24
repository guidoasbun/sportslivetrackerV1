#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/deploy.sh dev
#        ./scripts/deploy.sh prod

ENVIRONMENT=${1:-dev}
AWS_REGION="us-east-1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infrastructure"

echo "==> Fetching Terraform outputs for environment: $ENVIRONMENT"
cd "$INFRA_DIR"

PRODUCER_ECR=$(terraform output -raw ecr_producer_repository_url)
API_ECR=$(terraform output -raw ecr_api_repository_url)
FRONTEND_ECR=$(terraform output -raw ecr_frontend_repository_url)
CLUSTER=$(terraform output -raw ecs_cluster_name 2>/dev/null || echo "sportslivetracker-${ENVIRONMENT}-cluster")
PRODUCER_SERVICE=$(terraform output -raw producer_service_name 2>/dev/null || echo "sportslivetracker-${ENVIRONMENT}-producer")
API_SERVICE=$(terraform output -raw api_service_name 2>/dev/null || echo "sportslivetracker-${ENVIRONMENT}-api")
FRONTEND_SERVICE=$(terraform output -raw frontend_service_name 2>/dev/null || echo "sportslivetracker-${ENVIRONMENT}-frontend")
LAMBDA_FUNCTION=$(terraform output -raw lambda_function_name 2>/dev/null || echo "sportslivetracker-${ENVIRONMENT}-processor")

echo "==> Logging into ECR"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin \
    "$(echo "$API_ECR" | cut -d/ -f1)"

echo "==> Building producer image (linux/arm64)"
cd "$SCRIPT_DIR/.."
docker buildx build \
  --platform linux/arm64 \
  --file docker/producer.Dockerfile \
  --tag "$PRODUCER_ECR:latest" \
  --tag "$PRODUCER_ECR:$(git rev-parse --short HEAD)" \
  --push \
  ./producer

echo "==> Building api image (linux/arm64)"
docker buildx build \
  --platform linux/arm64 \
  --file docker/api.Dockerfile \
  --tag "$API_ECR:latest" \
  --tag "$API_ECR:$(git rev-parse --short HEAD)" \
  --push \
  .

echo "==> Building frontend image (linux/arm64)"
docker buildx build \
  --platform linux/arm64 \
  --file docker/frontend.Dockerfile \
  --tag "$FRONTEND_ECR:latest" \
  --tag "$FRONTEND_ECR:$(git rev-parse --short HEAD)" \
  --push \
  ./frontend

echo "==> Building and deploying Lambda"
./mvnw clean package -pl lambda -am
aws lambda update-function-code \
  --function-name "$LAMBDA_FUNCTION" \
  --zip-file fileb://lambda/target/lambda-0.0.1-SNAPSHOT.jar \
  --region "$AWS_REGION" > /dev/null

echo "==> Forcing new ECS deployments"
aws ecs update-service \
  --cluster "$CLUSTER" \
  --service "$PRODUCER_SERVICE" \
  --force-new-deployment \
  --region "$AWS_REGION" > /dev/null

aws ecs update-service \
  --cluster "$CLUSTER" \
  --service "$API_SERVICE" \
  --force-new-deployment \
  --region "$AWS_REGION" > /dev/null

aws ecs update-service \
  --cluster "$CLUSTER" \
  --service "$FRONTEND_SERVICE" \
  --force-new-deployment \
  --region "$AWS_REGION" > /dev/null

echo "==> Deploy complete. ECS is pulling the new images."
echo "    Monitor progress: AWS Console → ECS → $CLUSTER"

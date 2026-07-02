module "networking" {
  source = "./modules/networking"

  project_name = var.project_name
  environment  = var.environment
}

module "ecr" {
  source = "./modules/ecr"

  project_name = var.project_name
  environment  = var.environment
}

module "dynamodb" {
  source = "./modules/dynamodb"

  project_name = var.project_name
  environment  = var.environment
}

module "kinesis" {
  source = "./modules/kinesis"

  project_name = var.project_name
  environment  = var.environment
}

module "iam" {
  source = "./modules/iam"

  project_name = var.project_name
  environment  = var.environment

  kinesis_stream_arn               = module.kinesis.stream_arn
  events_table_arn                 = module.dynamodb.events_table_arn
  summaries_table_arn              = module.dynamodb.summaries_table_arn
  bedrock_model_id                 = var.bedrock_model_id
  api_sports_key_arn               = var.api_sports_key_arn
  cognito_google_client_id_arn     = var.cognito_google_client_id_arn
  cognito_google_client_secret_arn = var.cognito_google_client_secret_arn
  ecr_producer_repository_arn      = module.ecr.producer_repository_arn
  ecr_api_repository_arn           = module.ecr.api_repository_arn
  ecr_frontend_repository_arn      = module.ecr.frontend_repository_arn
  github_org                       = var.github_org
  github_repo                      = var.github_repo
  create_github_oidc_provider      = var.create_github_oidc_provider
}

module "lambda" {
  source = "./modules/lambda"

  project_name              = var.project_name
  environment               = var.environment
  lambda_execution_role_arn = module.iam.lambda_execution_role_arn
  kinesis_stream_arn        = module.kinesis.stream_arn
  events_table_name         = module.dynamodb.events_table_name
  summaries_table_name      = module.dynamodb.summaries_table_name
  bedrock_model_id          = var.bedrock_model_id
}

module "alb" {
  source = "./modules/alb"

  project_name      = var.project_name
  environment       = var.environment
  vpc_id            = module.networking.vpc_id
  public_subnet_ids = module.networking.public_subnet_ids
  domain_name       = var.domain_name
  www_domain_name   = var.www_domain_name
}

module "ecs" {
  source = "./modules/ecs"

  project_name            = var.project_name
  environment             = var.environment
  vpc_id                  = module.networking.vpc_id
  private_subnet_ids      = module.networking.private_subnet_ids
  alb_security_group_id   = module.alb.security_group_id
  ecs_execution_role_arn  = module.iam.ecs_execution_role_arn
  producer_task_role_arn  = module.iam.producer_task_role_arn
  frontend_task_role_arn  = module.iam.frontend_task_role_arn
  api_task_role_arn       = module.iam.frontend_task_role_arn
  target_group_arn        = module.alb.target_group_arn
  api_target_group_arn    = module.alb.api_target_group_arn
  ecr_producer_repository_url = module.ecr.producer_repository_url
  ecr_api_repository_url  = module.ecr.api_repository_url
  ecr_frontend_repository_url = module.ecr.frontend_repository_url
  kinesis_stream_name     = module.kinesis.stream_name
  api_sports_key_arn      = var.api_sports_key_arn
  events_table_name       = module.dynamodb.events_table_name
  summaries_table_name    = module.dynamodb.summaries_table_name
}

module "cognito" {
  source = "./modules/cognito"

  project_name             = var.project_name
  environment              = var.environment
  domain_name              = var.domain_name
  cognito_domain_prefix    = var.cognito_domain_prefix
  google_client_id_arn     = var.cognito_google_client_id_arn
  google_client_secret_arn = var.cognito_google_client_secret_arn
  allow_localhost          = var.allow_localhost
}

module "monitoring" {
  source = "./modules/monitoring"

  project_name                  = var.project_name
  environment                   = var.environment
  aws_region                    = var.aws_region
  lambda_function_name          = module.lambda.function_name
  ecs_cluster_name              = module.ecs.cluster_name
  ecs_producer_service_name     = module.ecs.producer_service_name
  ecs_api_service_name          = module.ecs.api_service_name
  ecs_frontend_service_name     = module.ecs.frontend_service_name
  kinesis_stream_name           = module.kinesis.stream_name
  dynamodb_events_table_name    = module.dynamodb.events_table_name
  dynamodb_summaries_table_name = module.dynamodb.summaries_table_name
}

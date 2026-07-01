environment = "prod"
aws_region  = "us-east-1"

api_sports_key_arn               = "REPLACE_WITH_PROD_ARN"
cognito_google_client_id_arn     = "REPLACE_WITH_PROD_ARN"
cognito_google_client_secret_arn = "REPLACE_WITH_PROD_ARN"

bedrock_model_id = "us.anthropic.claude-haiku-4-5-20251001-v1:0"

domain_name     = "gameshift.live"
www_domain_name = "www.gameshift.live"

github_org                  = "guidoasbun"
github_repo                 = "sportslivetrackerV1"
create_github_oidc_provider = false
cognito_domain_prefix       = "sports-tracker-prod-412381"
environment = "dev"
aws_region  = "us-east-1"

api_sports_key_arn               = "arn:aws:secretsmanager:us-east-1:412381751532:secret:sports-tracker/dev/api-sports-key-1vbRqS"
cognito_google_client_id_arn     = "arn:aws:secretsmanager:us-east-1:412381751532:secret:sports-tracker/dev/google-client-id-8r3JvX"
cognito_google_client_secret_arn = "arn:aws:secretsmanager:us-east-1:412381751532:secret:sports-tracker/dev/google-client-secret-Ol1mVP"

bedrock_model_id = "anthropic.claude-3-haiku-20240307-v1:0"

domain_name = "dev.gameshift.live"

github_org                  = "guidoasbun"
github_repo                 = "sportslivetrackerV1"
create_github_oidc_provider = true
cognito_domain_prefix       = "sports-tracker-dev-412381"
allow_localhost             = true
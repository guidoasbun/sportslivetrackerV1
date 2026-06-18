output "function_name" {
  description = "Name of the Lambda function"
  value       = aws_lambda_function.processor.function_name
}

output "function_arn" {
  description = "ARN of the Lambda function"
  value       = aws_lambda_function.processor.arn
}

output "artifacts_bucket_name" {
  description = "S3 bucket name where CI/CD uploads the compiled JAR"
  value       = aws_s3_bucket.lambda_artifacts.id
}

output "artifacts_bucket_arn" {
  description = "ARN of the Lambda artifacts S3 bucket (needed to grant CI/CD write access)"
  value       = aws_s3_bucket.lambda_artifacts.arn
}

output "lambda_role_name" {
  value = aws_iam_role.lambda_role.name
}

output "lambda_role_arn" {
  value = aws_iam_role.lambda_role.arn
}

/*output "lambda_layer_arn" {
   value = aws_lambda_layer_version.aws-java-base-layer.arn
}

output "lambda_layer_version_arn" {
   value =  aws_lambda_layer_version.aws-java-base-layer.layer_arn
}

output "aws_iam_policy_lambda_logging_arn" {
  value = aws_iam_policy.lambda_logging.arn
}

output "version" {
  description = "This Lamba Layer version."
  value       = "${aws_lambda_layer_version.lambda_layer.version}"
}


output "arn" {
  description = "The Amazon Resource Name (ARN) of the Lambda Layer with version."
  value       = "${aws_lambda_layer_version.lambda_layer.arn}"
}

output "layer_arn" {
  description = "The Amazon Resource Name (ARN) of the Lambda Layer without version"
  value       = "${aws_lambda_layer_version.lambda_layer.layer_arn}"
}*/

/*output "arn" {
    value = aws_elasticsearch_domain.es.arn
} 
output "domain_id" {
    value = aws_elasticsearch_domain.es.domain_id
} 
output "domain_name" {
    value = aws_elasticsearch_domain.es.domain_name
} 
output "endpoint" {
    value = aws_elasticsearch_domain.es.endpoint
} 
output "kibana_endpoint" {
    value = aws_elasticsearch_domain.es.kibana_endpoint
}*/
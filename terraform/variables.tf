#VARIABLES USED ACROSS ALL RESOURCES
variable "application" {
  type        = string
  description = "name of application"
  default     = "WF1-WFDM"
}

variable "application_lowercase" {
  type        = string
  description = "application name in lowercase"
  default     = "wfdm"
}

variable "customer" {
  type        = string
  description = "name of customer"
  default     = "WildFireOne"
}

variable "tool" {
  type        = string
  description = "name of aws tool being used"
  default     = "opensearch"
}

variable "domain" {
  type        = string
  description = "name of opensearch domain"
  default     = null
}

variable "region" {
  type        = string
  description = "AWS Region, where to deploy cluster."
  default     = "ca-central-1"
}

variable "accountNum" {
  type        = string
  description = "account number of AWS account running script"
  default     = null
}

variable "custom_endpoint_url" {
  type        = string
  description = "URL matching custom endpoint cert"
  default     = "bcwildfireservices.com"
}

variable "env" {
  type        = string
  description = "Suffix appended to all managed resource names to indicate their environment"
  default     = null
}

variable "env_lowercase" {
  type        = string
  description = "env in lowercase"
  default     = null
}

variable "env_full" {
  type        = string
  description = "full name of environment (i.e. INTEGRATION)"
  default     = null
}

variable "pr" {
  type        = string
  description = "Suffix appended to all managed resource names"
  default     = "0"
}

variable "suffix" {
  type        = string
  description = "Suffix appended to all managed resource names"
  default     = null
}

#s3-related variables

variable "s3BucketName" {
  type        = string
  description = "name of s3 bucket"
  default     = null
}

variable "clamAVBucketName" {
  type        = string
  description = "name of clamAV specific s3 bucket"
  default     = null
}

#SQS-specific variables

variable "clamQueue" {
  type    = string
  default = null
}

variable "maxReceivedCount" {
  type        = number
  description = "How many messages can be placed into the deadletter queue"
  default     = 4
}

variable "visibilityTimeoutSeconds" {
  type        = number
  description = "Suffix appended to all managed resource names"
  default     = 60
}

#VPC-RELATED VARIABLES
variable "vpc_id" {
  type        = string
  description = "ID of existing VPC to be used"
  default     = null
}

variable "public_subnet_id" {
  type        = string
  description = "id of existing public subnet to be used"
  default     = null
}

variable "private_subnet_id" {
  type        = string
  description = "id of existing private subnet to be used"
  default     = null
}

variable "internet_gateway_id" {
  type        = string
  description = "ID of existing internet gateway to use"
  default     = null
}

variable "security_group_id" {
  type        = string
  default     = null
  description = "id of existing security group to use"
}

variable "vpc_cidr_block" {
  type        = string
  description = "CIDR block to be used when creating vpc"
  default     = "10.0.0.0/16"
}

variable "public_subnet_block" {
  type        = string
  description = "CIDR block used when creating public subnet"
  default     = "10.0.0.0/24"
}

variable "private_subnet_block" {
  type        = string
  description = "CIDR block of private subnet"
  default     = "10.0.1.0/24"
}

//VARIABLES FOR OPENSEARCH
variable "ElasticSearch_Version" {
  type        = string
  description = "Version of ElasticSearch or OpenSearch to use"
  default     = "OpenSearch_1.1"
}

variable "opensearchDomainName" {
  type        = string
  description = "name for opensearch domain"
  default     = null
}

variable "custom_endpoint_certificate_arn" {
  type        = string
  description = "Custom Endpoint Certificate ARN"
  default     = "arn:aws:acm:ca-central-1:460053263286:certificate/fcbb5aed-fa01-434c-a783-ec4596bc02df"
}

variable "iit_lambda_code_bucket_key_version" {
  type        = string
  description = "Lambda Code Package Version"
  default     = null
}

variable "target_aws_account_id" {
  type        = string
  description = "target_aws_account_id"
  default     = null
}

variable "target_env" {
  type        = string
  description = "target_env"
  default     = null
}

variable "master_node_instance_count" {
  type    = number
  default = 0
}

variable "master_node_instance_type" {
  type    = string
  default = "c6g.large.elasticsearch"
}


variable "master_node_usage" {
  type    = string
  default = "false"
}

variable "data_node_instance_count" {
  type    = number
  default = 1
}

variable "data_node_instance_type" {
  type    = string
  default = "t3.small.elasticsearch"
}

variable "ebs_volume_size" {
  type    = number
  default = 10
}

variable "kinesis_shards" {
  type    = number
  default = 2
}

variable "ultrawarm_node_instance_count" {
  type    = number
  default = null
}

variable "ultrawarm_node_instance_type" {
  type    = string
  default = "ultrawarm1.medium.elasticsearch"
}

variable "OPENSEARCH_PASSWORD" {
  type        = string
  default     = null
  description = "The opensearch password. Received as a secret from github"
}

/*
variable "ultrawarm_node_volume_size" {
  type = number
  default = null
}
*/

#LAMBDA-RELATED VARIABLES

variable "document_token_url" {
  type        = string
  description = "govt-side token url"
  default     = null
}

variable "lambda_function_handler" {
  type    = string
  default = null
}

variable "indexing_function_handler" {
  type    = string
  default = null
}

variable "clamav_function_handler" {
  type    = string
  default = null
}

variable "lambda_payload_filename" {
  type    = string
  default = null
}

variable "lambda_initializer_filename" {
  type    = string
  default = null
}

variable "lambda_clamav_filename" {
  type    = string
  default = null
}

variable "java_layer_name" {
  type    = string
  default = "aws-java-base-layer-terraform"
}

variable "layer_file_name" {
  type    = string
  default = "java.zip"
}

variable "aws_ip_allocation_id" {
  type    = string
  default = null
}

variable "main_route53_zone" {
  type    = string
  default = null
}

variable "document_api_url" {
  type        = string
  description = "url of govt-side API"
  default     = null
}

variable "document_index_account_name" {
  type    = string
  default = "WFDM_DOCUMENTS_INDEX"
}

variable "documents_index_password" {
  type    = string
  default = "Password"
}

//secret manager is parameterized
//because each ENV. use different password
/*
variable "secret_manager" {
  type    = string
  default = "WFDM_DOC_INDEX_ACCOUNT_PASSWORD"
}*/

variable "virus_alert" {
  type    = string
  default = "arn:aws:sns:ca-central-1:460053263286:WFDM_CLAMAV_EMAIL_NOTIFICATION"
}

variable "memory_size" {
  type    = number
  default = 1028
}

variable "timeout_length" {
  type    = number
  default = 45
}

variable "sns_email_receivers" {
  type    = list(any)
  default = []
}

variable "clamAVStackName" {
  type    = string
  default = null
}

variable "secret_manager_name" {
  type        = string
  description = "The AWS Secret Manager secret name"
  default     = null
}
#VARIABLES USED ACROSS ALL RESOURCES
variable "application" {
  type        = string
  description = "name of application"
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
  type = string
  description = "name of opensearch domain"
  default = null
}

variable "region" {
  type        = string
  description = "AWS Region, where to deploy cluster."
  default     = "ca-central-1"
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


#VPC-RELATED VARIABLES
variable "vpc_cidr_block" {
  type        = string
  description = "CIDR block to be used by vpc"
  default     = "10.0.0.0/16"
}

variable "public_subnet_block" {
  type        = string
  description = "CIDR block of public subnet"
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

/*
variable "ultrawarm_node_volume_size" {
  type = number
  default = null
}
*/

#LAMBDA-RELATED VARIABLES

variable "lambda_function_handler" {
	type = string
  default = null
}

variable "lambda_payload_filename" {
  type = string
  default = null
}

variable "java_layer_name" {
  type        = string
  default     = "aws-java-base-layer-terraform"
}

variable "layer_file_name" {
  type        = string
  default = "java.zip"
}

variable "aws_ip_allocation_id" {
  type = string
  default = null
}

variable "main_route53_zone" {
  type = string
  default = null
}


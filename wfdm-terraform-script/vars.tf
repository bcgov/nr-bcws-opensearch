variable "iam_role_lambda_function"{
	type = string
}

variable "queue_name" {
    type = string
}

variable "s3_bucket_name" {
    type = string
}

variable "domain" {
    type = string
}
variable "instance_type" {
    type = string
}
variable "tag_domain" {
    type = string
}
variable "volume_type" {
    type = string
}
variable "ebs_volume_size" {}

variable "lambda_function_handler" {
	type = string
}

variable "lambda_payload_filename" {
  type = string
}

variable "layer_name" {
  type        = string
}

/*variable "filename" {
  type        = string
  description = "The path to the function's deployment package within the local filesystem. If defined, The s3_-prefixed options cannot be used."
  default     = ""
}

variable "s3_bucket" {
  type        = string
  description = "The S3 bucket location containing the function's deployment package. Conflicts with filename. This bucket must reside in the same AWS region where you are creating the Lambda function."
  default     = ""
}

variable "s3_key" {
  type        = string
  description = "The S3 key of an object containing the function's deployment package. Conflicts with filename."
  default     = ""
}

variable "s3_object_version" {
  type        = string
  description = "he object version containing the function's deployment package. Conflicts with filename."
  default     = ""
}

variable "description" {
  type        = string
  description = "Description of what your Lambda Layer does."
}

variable "compatible_runtimes" {
  type        = list
  description = "A list of Runtimes this layer is compatible with. Up to 5 runtimes can be specified."
  default     = []
}

variable "license_info" {
  type        = string
  description = " License info for your Lambda Layer"
  default     = ""
}*/

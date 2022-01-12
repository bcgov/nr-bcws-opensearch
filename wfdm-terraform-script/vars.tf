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

variable "layer_file_name" {
  type        = string
}



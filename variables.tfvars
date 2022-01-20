//VARIABLES USED ACROSS ALL RESOURCES

variable "application" {
    type="string"
    description="name of application"
    default="WFDM"
}

variable "customer" {
    type="string"
    description="name of customer"
    default=WildFireOne
}

variable "tool" {
    type="string"
    description="name of aws tool being used"
    default="opensearch"
}

variable "region" {
  type = string
  description = "AWS Region, where to deploy cluster."
  default = "ca-central-1"
}

variable "custom_endpoint_url" {
    type=string
    description="URL matching custom endpoint cert"
    default="bcwildfireservices.com"
}

variable "env" {
  type = string
  description = "Suffix appended to all managed resource names to indicate their environment"
  default = null
}

variable "pr" {
  type = string
  description = "Suffix appended to all managed resource names"
  default = "0"
}

variable "suffix" {
  type = string
  description = "Suffix appended to all managed resource names"
  default = null
}

//VPC-RELATED VARIABLES
variable "vpc_cidr_block" {
  type=string
  description="CIDR block to be used by vpc"
  default="10.0.0.0/16"
}
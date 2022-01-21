terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "3.72.0"
    }
  }
  required_version = ">= 1.1.0"

  cloud {
    organization = "vivid-solutions"

    workspaces {
      name = "nr-bcws-opensearch"
    }
  }
}
//Variable declarations (values in terraform.auto.tfvars)
#VARIABLES USED ACROSS ALL RESOURCES
variable "application" {
  type        = string
  description = "name of application"
  default     = "WFDM"
}

variable "customer" {
  type        = string
  description = "name of customer"
  default     = WildFireOne
}

variable "tool" {
  type        = string
  description = "name of aws tool being used"
  default     = "opensearch"
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


//CREATE THE VPC AND SUBNETS
//Main VPC

resource "aws_vpc_ipam" "test" {
  operating_regions {
    region_name = var.region
  }
}

resource "aws_vpc_ipam_pool" "test" {
  address_family = "ipv4"
  ipam_scope_id  = aws_vpc_ipam.test.private_default_scope_id
  locale         = var.region
}

resource "aws_vpc_ipam_pool_cidr" "test" {
  ipam_pool_id = aws_vpc_ipam_pool.test.id
  cidr         = var.vpc_cidr_block
}

resource "aws_vpc" "main_vpc" {
  ipv4_ipam_pool_id   = aws_vpc_ipam_pool.test.id
  ipv4_netmask_length = 16
  depends_on = [
    aws_vpc_ipam_pool_cidr.test
  ]
  instance_tenancy = "default"

  tags = {
    Name        = "${var.application}-vpc-${var.env}"
    Application = "${var.application}"
    Customer    = "${var.customer}"
    Environment = "${var.env}"
  }
}

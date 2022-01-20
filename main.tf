terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "3.26.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.0.1"
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

//CREATE THE VPC AND SUBNETS
//Main VPC
variable "vpc_cidr_block" {
  type=string
  description="CIDR block to be used by vpc"
  default="10.0.0.0/16"
}

resource "aws_vpc" "main_vpc" {
    cidr_block = "${var.vpc_cidr_block}"
    operating_regions {
        region_name=var.region
    }
    tags {
        Name = "${var.application}-vpc-${var.env}"
        Application = var.application
        Customer = var.customer
        Environment = var.env
    }
}

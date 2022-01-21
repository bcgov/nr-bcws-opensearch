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
variable "application" {}
variable "env" {}
variable "customer" {}
variable "vpc_cidr_block" {}
variable "region" {}


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

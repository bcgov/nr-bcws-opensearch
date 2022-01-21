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
  default     = "WildFireOne"
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






provider "aws" {
  region = var.region
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

variable "route" {
  type = object({
    cidr_block = string
    gateway_id = string
  })
}



//CREATE THE VPC AND SUBNETS
//Main VPC
resource "aws_vpc" "main_vpc" {
  cidr_block = var.vpc_cidr_block

  tags = {
    Name        = "${var.application}-vpc-${var.env}"
    Application = "${var.application}"
    Customer    = "${var.customer}"
    Environment = "${var.env}"
  }
}

resource "aws_internet_gateway" "main_internet_gateway" {
  vpc_id = aws_vpc.main_vpc.id
  tags = {
    Name        = "${var.application}-internet-gateway-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_subnet" "private_subnet" {
  vpc_id     = aws_vpc.main_vpc.id
  cidr_block = var.private_subnet_block
  tags = {
    Name        = "${var.application}-private-subnet-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_nat_gateway" "main_nat_gateway" {
  subnet_id = aws_subnet.private_subnet.id
  tags = {
    Name        = "${var.application}-nat-gateway-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_route_table" "main_route_table" {
  depends_on = [aws_nat_gateway.main_nat_gateway]
  vpc_id     = aws_vpc.main_vpc.id
  route = [{
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_nat_gateway.main_nat_gateway.id
    # workaround as suggested here: https://github.com/hashicorp/terraform-provider-aws/issues/20756#issuecomment-913284042
    carrier_gateway_id         = ""
    destination_prefix_list_id = ""
    egress_only_gateway_id     = ""
    instance_id                = ""
    ipv6_cidr_block            = ""
    local_gateway_id           = ""
    nat_gateway_id             = ""
    network_interface_id       = ""
    transit_gateway_id         = ""
    vpc_endpoint_id            = ""
    vpc_peering_connection_id  = ""
  }]
  tags = {
    Name        = "${var.application}-main-route-table-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_subnet" "public_subnet" {
  vpc_id                  = aws_vpc.main_vpc.id
  cidr_block              = var.public_subnet_block
  map_public_ip_on_launch = true
  tags = {
    Name        = "${var.application}-public-subnet-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_route_table" "public_route_table" {
  depends_on = [aws_internet_gateway.main_internet_gateway]
  vpc_id     = aws_vpc.main_vpc.id
  route = [{
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main_internet_gateway.id
    # workaround as suggested here: https://github.com/hashicorp/terraform-provider-aws/issues/20756#issuecomment-913284042
    carrier_gateway_id         = ""
    destination_prefix_list_id = ""
    egress_only_gateway_id     = ""
    instance_id                = ""
    ipv6_cidr_block            = ""
    local_gateway_id           = ""
    nat_gateway_id             = ""
    network_interface_id       = ""
    transit_gateway_id         = ""
    vpc_endpoint_id            = ""
    vpc_peering_connection_id  = ""
  }]
  tags = {
    Name        = "${var.application}-public-route-table-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_route_table_association" "main_association" {
  subnet_id      = aws_subnet.private_subnet.id
  route_table_id = aws_route_table.main_route_table.id
}

resource "aws_route_table_association" "public_association" {
  subnet_id      = aws_subnet.public_subnet.id
  route_table_id = aws_route_table.public_route_table.id
}




#Create OpenSearch and related resources
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

variable "data_node_volume_size" {
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

variable "domain" {
  type = string
  default = "WFDM"
}
/*
variable "ultrawarm_node_volume_size" {
  type = number
  default = null
}
*/

#COMPONENTS FOR OPENSEARCH


data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

resource "aws_security_group" "es" {
  name        = "${var.application}-elasticsearch-security-group-${var.env}"
  description = "Managed by Terraform"
  vpc_id      = aws_vpc.main_vpc

  ingress {
    from_port = 443
    to_port   = 443
    protocol  = "tcp"

    cidr_blocks = [
      var.private_subnet_block
    ]
  }
}

resource "aws_iam_service_linked_role" "es" {
  aws_service_name = "es.amazonaws.com"
}

resource "aws_elasticsearch_domain" "main_elasticsearch_domain" {
  domain_name           = "${var.application}-${var.tool}-${var.env}"
  elasticsearch_version = var.ElasticSearch_Version
  cluster_config {
    dedicated_master_count  = var.master_node_instance_count
    dedicated_master_enabled = var.master_node_usage
    dedicated_master_type   = var.master_node_instance_type
    instance_count          = var.data_node_instance_count
    instance_type           = var.data_node_instance_type
    warm_count              = var.ultrawarm_node_instance_count
    warm_type               = var.ultrawarm_node_instance_type

  }
  ebs_options {
    ebs_enabled = "true"
    volume_size = var.data_node_volume_size
  }

  vpc_options {
    subnet_ids = [
      aws_subnet.private_subnet.id
    ]

    security_group_ids = [aws_security_group.es.id]
  }

  advanced_options = {
    "rest.action.multi.allow_explicit_index" = "true"
  }

  access_policies = <<CONFIG
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "es:*",
            "Principal": "*",
            "Effect": "Allow",
            "Resource": "arn:aws:es:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:domain/${var.domain}/*"
        }
    ]
}
CONFIG
}


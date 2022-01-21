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

//CREATE THE VPC AND SUBNETS
//Main VPC

resource "aws_vpc" "main_vpc" {
    cidr_block = "${var.vpc_cidr_block}"
    operating_regions {
        region_name=var.region
    }
    tags {
        Name = "${var.application}-vpc-${var.env}"
        Application = "${var.application}"
        Customer = "${var.customer}"
        Environment = "${var.env}"
    }
}

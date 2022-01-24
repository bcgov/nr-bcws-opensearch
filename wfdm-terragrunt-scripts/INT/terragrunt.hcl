terraform {
    source = "../..//terraform"
}

locals {
  target_env = "INT"
}

generate "backend" {
  path = "backend.tf"
  if_exists = "overwrite_terragrunt"
  contents = <<EOF
terraform {
  backend "remote" {
    organization = "vivid-solutions"
    workspaces {
        name = "nr-bcws-opensearch"
    }
  }
}
EOF
}

remote_state {
    backend = "remote"
    config = { }
}

generate "inputs" {
  path = "terraform.auto.tfvars"
  if_exists = "overwrite_terragrunt"
  contents = <<EOF
  env = "${local.target_env}"
  domain = "WFDM-opensearch-${local.target_env}"
EOF
}
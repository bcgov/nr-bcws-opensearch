terraform {
    source = "../..//terraform"
}

locals {
  application = "WF1-WFDM"
  application_lowercase = "wfdm"
  target_env = "TST"
  env_lowercase = "tst"
  env_full = "TEST"
  document_api_url = "https://t1bcwsapi.nrs.gov.bc.ca/wfdm-document-management-api/documents/"
  document_token_url = "https://testapps.nrs.gov.bc.ca/pub/oauth2/v1/oauth/token?disableDeveloperFilter=true&grant_type=client_credentials"
  clamAVStackName = "WfdmClamavStackTST"
  clamstackQueue = "WfdmClamavStackTST-wfdmClamscanQueuetstAF294E61-P6JScScVrkzs"
  secret_manager_name = "WFDM_DOC_INDEX_ACCOUNT_PASSWORD_TST"
}

generate "backend" {
  path = "backend.tf"
  if_exists = "overwrite_terragrunt"
  contents = <<EOF
terraform {
  backend "remote" {
    organization = "wf1-wfdm-opensearch"
    workspaces {
        name = "nr-bcws-opensearch-tst"
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
  opensearchDomainName = "wf1-${local.application_lowercase}-opensearch-${local.env_lowercase}"
  s3BucketName = "${local.application_lowercase}-s3-bucket-${local.env_lowercase}"
  clamAVStackName =  "${local.clamAVStackName}"
  env_lowercase = "${local.env_lowercase}"
  application_lowercase = "${local.application_lowercase}"
  env_full = "${local.env_full}"
  document_api_url = "${local.document_api_url}"
  document_token_url = "${local.document_token_url}"
  clamQueue = "${local.clamstackQueue}"
EOF
}

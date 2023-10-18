terraform {
    source = "../..//terraform"
}

locals {
  application = "WF1-WFDM"
  application_lowercase = "wfdm"
  target_env = "INT"
  env_lowercase = "int"
  env_full = "INTEGRATION"
  document_api_url = "https://i1bcwsapi.nrs.gov.bc.ca/wfdm-document-management-api/documents/"
  document_token_url = "https://intapps.nrs.gov.bc.ca/pub/oauth2/v1/oauth/token?disableDeveloperFilter=true&grant_type=client_credentials"
  clamAVStackName = "WfdmClamavStackINT"
  clamstackQueue = "WfdmClamavStackINT-wfdmClamscanQueueintC7DDA541-4i0O18IjJgs0"
  opensearch_password = get_env("opensearch_password")
  opensearch_user = get_env("opensearch_user")
}

generate "backend" {
  path = "backend.tf"
  if_exists = "overwrite_terragrunt"
  contents = <<EOF
terraform {
  backend "s3" {
    bucket         = "wfdm-terraform-remote-state-int"
    key            = "wfdm-opensearch-statefile-int"
    region         = "ca-central-1"
    dynamodb_table = "wfdm-remote-state-lock-int"
    encrypt        = true
  }
}
EOF
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
  secret_manager_name = "WFDM_DOC_INDEX_ACCOUNT_PASSWORD_${local.target_env}"
  opensearch_user = "${local.opensearch_user}"
  opensearch_password = "${local.opensearch_password}"
  ElasticSearch_Version = "OpenSearch_2.5"
EOF
}
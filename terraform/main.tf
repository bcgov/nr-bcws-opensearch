terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.6.0"
    }
  }
  required_version = ">= 1.1.0"
}

data "aws_vpc" "main_vpc" {
  id = var.vpc_id
}

data "aws_subnet" "public_subnet" {
  id = var.public_subnet_id
}

data "aws_subnet" "private_subnet" {
  id = var.private_subnet_id
}

data "aws_internet_gateway" "main_internet_gateway" {
  internet_gateway_id = var.internet_gateway_id
}

data "aws_security_group" "es" {
  id = var.security_group_id
}


/* The following creates a new VPC with its own subnets.
   Use it if you cannot use the existing VPC
 
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
  subnet_id     = aws_subnet.private_subnet.id
  allocation_id = var.aws_ip_allocation_id
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

*/





# Creating IAM role so that Lambda service to assume the role and access other  AWS services. 

//MAIN ROLE USED BY OPENSEARCH-INDEXING-FUNCTION
resource "aws_iam_role" "lambda_role" {
  name = "${var.application}-iam_role_lambda_index_searching-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": [
                    "lambda.amazonaws.com",
                    "apigateway.amazonaws.com",
                    "opensearch.amazonaws.com"
                ]
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

# Policy Attachment on the roles.

//policy_attach_lambda_logging
resource "aws_iam_role_policy_attachment" "policy_attach" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}
//policy_attach_s3_full_access
resource "aws_iam_role_policy_attachment" "policy_attach_sqs" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = data.aws_iam_policy.s3-full-access-policy.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_vpc_execution" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = data.aws_iam_policy.lambda-vpc-access-execution.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_es_write" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.elasticsearch-access.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_secret_manager" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = data.aws_iam_policy.secretsmanager-readwrite.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_sqs_for_lambda" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.sqs-lambda-permission.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_full_lambda" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = data.aws_iam_policy.lambdaFullAccess.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_full_opensearch" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = data.aws_iam_policy.opensearchFullAccess.arn
}

//ROLE USED BY OPENSEARCH-INDEXING-INITIALIZER
resource "aws_iam_role" "lambda_initializer_role" {
  name = "${var.application}-iam_role_lambda_index_initializer-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_initializer_logging" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_initializer_sns_publish" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = aws_iam_policy.sns-publish.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_initializer_secret_manager" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = data.aws_iam_policy.secretsmanager-readwrite.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_initializer_sqs" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = data.aws_iam_policy.sqs-full-access-policy.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_initializer_kms" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = aws_iam_policy.kms-full-access-policy.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_initializer_s3" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = data.aws_iam_policy.s3-full-access-policy.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_initializer_vpc_execution" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = data.aws_iam_policy.lambda-vpc-access-execution.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_initializer_full" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = data.aws_iam_policy.lambda-full-access.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_initializer_es" {
  role       = aws_iam_role.lambda_initializer_role.name
  policy_arn = aws_iam_policy.elasticsearch-access.arn
}


//ROLE USED BY CLAMAV LAMBDA FUNCTION
resource "aws_iam_role" "lambda_clamav_role" {
  name = "${var.application}-iam_role_lambda_clamav-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "policy_attach_clamav_s3" {
  role       = aws_iam_role.lambda_clamav_role.name
  policy_arn = data.aws_iam_policy.s3-full-access-policy.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_clamav_logging" {
  role       = aws_iam_role.lambda_clamav_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_clamav_sns_publish" {
  role       = aws_iam_role.lambda_clamav_role.name
  policy_arn = aws_iam_policy.sns-publish.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_clamav_secret_manager" {
  role       = aws_iam_role.lambda_clamav_role.name
  policy_arn = data.aws_iam_policy.secretsmanager-readwrite.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_lambda_clamav_full" {
  role       = aws_iam_role.lambda_clamav_role.name
  policy_arn = data.aws_iam_policy.lambda-full-access.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_clamav_sqs" {
  role       = aws_iam_role.lambda_clamav_role.name
  policy_arn = data.aws_iam_policy.sqs-full-access-policy.arn
}


resource "aws_iam_role" "opensearch_sqs_role" {
  name = "${var.application}-iam-role-opensearch-sqs-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
     {
       "Action": "sts:AssumeRole",
        "Principal": {
          "Service": [
            "opensearch.amazonaws.com",
            "apigateway.amazonaws.com"
          ]
        },
        "Effect": "Allow",
        "Sid": ""
     }
   ]
}
EOF
}



resource "aws_iam_role_policy_attachment" "sqs-api-exec-role" {
  role       = aws_iam_role.opensearch_sqs_role.name
  policy_arn = aws_iam_policy.sqs-iam-policy.arn
}



# SQS queue

resource "aws_sqs_queue" "deadletter" {
  name = "${var.application}-sqs-deadletter-${var.env}"

  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_sqs_queue" "queue" {
  depends_on                 = [aws_iam_role.opensearch_sqs_role]
  visibility_timeout_seconds = var.timeout_length_large
  name                       = "${var.application}-sqs-queue-${var.env}"



  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.deadletter.arn
    maxReceiveCount     = "${var.maxReceivedCount}"

  })
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue",
    sourceQueueArns   = ["${aws_sqs_queue.deadletter.arn}"]
  })

  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Id": "Policy1640124887139",
  "Statement": [
    {
      "Sid": "Stmt1640121864964",
      "Effect": "Allow",
      "Principal": "*",
      "Action": [
        "sqs:ListDeadLetterSourceQueues",
        "sqs:ListQueueTags",
        "sqs:ListQueues",
        "sqs:ReceiveMessage",
        "sqs:SendMessage"
      ],
      "Resource": "arn:aws:sqs:*:${data.aws_caller_identity.current.account_id}:${var.application}-sqs-queue-${var.env}"
    }
  ]
}
POLICY
}


/*
# Event source from SQS
resource "aws_lambda_event_source_mapping" "event_source_mapping" {
  event_source_arn = aws_sqs_queue.queue.arn
  enabled          = true
  function_name    = aws_lambda_function.terraform_wfdm_indexing_function.arn
  batch_size       = 1
}
*/




#Create s3 bucket and roles, policies needed
data "aws_s3_bucket" "terraform-s3-bucket" {
  bucket = var.s3BucketName
}


data "aws_cloudformation_stack" "cdk_stack" {
  name = var.clamAVStackName
}


data "aws_s3_bucket" "clamav-bucket" {
  bucket = data.aws_cloudformation_stack.cdk_stack.outputs["oBucketName"]
}


resource "aws_iam_role" "s3-bucket-add-remove-role" {
  name = "${var.application}-s3-bucket-add-remove-role-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
     {
       "Action": "sts:AssumeRole",
        "Principal": {
          "Service": "lambda.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
     }
   ]
}
EOF
}

resource "aws_iam_role" "s3-clamav-bucket-role" {
  name = "${var.application}-s3-clamav-role-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
     {
       "Action": "sts:AssumeRole",
        "Principal": {
          "Service": "lambda.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
     }
   ]
}
EOF
}

resource "aws_iam_role" "lambda-clamav-role" {
  name = "${var.application}-lambda-clamav-role-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
     {
       "Action": "sts:AssumeRole",
        "Principal": {
          "Service": "lambda.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
     }
   ]
}
EOF
}

#Upload java.zip to s3bucket
/*
resource "aws_s3_bucket_object" "java_zip" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.id
  key    = var.layer_file_name
  acl    = "private"
  source = "aws-lambda-layer-base/java.zip"
  tags = {
    Name        = "${var.application}-s3-bucket-object-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}
*/

data "aws_s3_bucket_object" "java_zip" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key    = var.layer_file_name
}

data "aws_s3_bucket_object" "s3_lambda_payload_object" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key    = var.lambda_payload_filename
}

data "aws_s3_bucket_object" "s3_lambda_initializer_object" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key    = var.lambda_initializer_filename
}

data "aws_s3_bucket_object" "s3_lambda_clamav_object" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key    = var.lambda_clamav_filename
}

data "aws_s3_bucket_object" "indexing_function_hash" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key = var.lambda_payload_hash_name 
}

data "aws_s3_bucket_object" "indexing_initializer_hash" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key = var.lambda_initializer_hash_name
}

data "aws_s3_bucket_object" "clamav_function_hash" {
  bucket = data.aws_s3_bucket.terraform-s3-bucket.bucket
  key = var.lambda_clamav_hash_name 
}

resource "aws_lambda_layer_version" "aws-java-base-layer-terraform" {
  layer_name          = "${var.application}-${var.java_layer_name}-${var.env}"
  s3_bucket           = data.aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key              = var.layer_file_name
  description         = "Common layer with java jars files"
  compatible_runtimes = ["java17"]
}



#Lambda Function Handler
resource "aws_lambda_function" "terraform_wfdm_indexing_function" {
  function_name = "${var.application}-indexing-function-${var.env}"
  s3_bucket     = data.aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key        = var.lambda_payload_filename
  role          = aws_iam_role.lambda_role.arn
  handler       = var.lambda_function_handler
  source_code_hash = "${data.aws_s3_bucket_object.indexing_function_hash.body}"
  runtime     = "java17"
  layers      = ["${aws_lambda_layer_version.aws-java-base-layer-terraform.arn}"]
  memory_size = var.memory_size
  timeout     = var.timeout_length

  tags = {
    Name        = "${var.application}-indexing-function-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  environment {
    variables = {
      ENVIRONMENT                              = "${var.env_full}"
      WFDM_DOCUMENT_API_URL                    = "${var.document_api_url}"
      WFDM_DOCUMENT_CLAMAV_S3BUCKET            = data.aws_s3_bucket.clamav-bucket.bucket
      WFDM_DOCUMENT_INDEX_ACCOUNT_NAME         = var.document_index_account_name
      WFDM_DOCUMENT_INDEX_ACCOUNT_PASSWORD     = var.documents_index_password
      WFDM_DOCUMENT_OPENSEARCH_DOMAIN_ENDPOINT = "${var.opensearchDomainName}.${var.domain}"
      WFDM_DOCUMENT_OPENSEARCH_INDEXNAME       = aws_elasticsearch_domain.main_elasticsearch_domain.domain_name
      WFDM_DOCUMENT_SECRET_MANAGER             = "${var.secret_manager_name}"
      WFDM_DOCUMENT_TOKEN_URL                  = "${var.document_token_url}"
      WFDM_DOCUMENT_SUPPORTED_MIME_TYPES       = join(",",var.supported_mime_types)
      WFDM_DOCUMENT_FILE_SIZE_SCAN_LIMIT       = "${var.file_scan_size_limit}"
    }
  }
  depends_on = [
    aws_lambda_layer_version.aws-java-base-layer-terraform
  ]
}

#Lambda File Indexing Initializer
resource "aws_lambda_function" "terraform_indexing_initializer_function" {
  function_name = "${var.application}-indexing-initializer-${var.env}"
  s3_bucket     = data.aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key        = var.lambda_initializer_filename
  role          = aws_iam_role.lambda_initializer_role.arn
  handler       = var.indexing_function_handler
  source_code_hash = "${data.aws_s3_bucket_object.indexing_initializer_hash.body}"
  runtime     = "java17"
  layers      = ["${aws_lambda_layer_version.aws-java-base-layer-terraform.arn}"]
  memory_size = var.memory_size
  timeout     = var.timeout_length_large
  tags = {
    Name        = "${var.application}-indexing-initializer-function-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  environment {
    variables = {
      ENVIRONMENT                        = "${var.env_full}"
      WFDM_DOCUMENT_API_URL              = "${var.document_api_url}"
      WFDM_DOCUMENT_CLAMAV_S3BUCKET      = data.aws_s3_bucket.clamav-bucket.bucket
      WFDM_DOCUMENT_TOKEN_URL            = "${var.document_token_url}"
      WFDM_INDEXING_LAMBDA_NAME          = aws_lambda_function.terraform_wfdm_indexing_function.function_name
      WFDM_DOCUMENT_SECRET_MANAGER       = "${var.secret_manager_name}"
      WFDM_DOCUMENT_FILE_SIZE_SCAN_LIMIT = "${var.file_scan_size_limit}"

    }
  }
  depends_on = [
    aws_lambda_layer_version.aws-java-base-layer-terraform
  ]
}

#Lambda ClamAV handler
resource "aws_lambda_function" "lambda_clamav_handler" {
  function_name = "${var.application}-clamav-handler-${var.env}"
  s3_bucket     = data.aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key        = var.lambda_clamav_filename
  role          = aws_iam_role.lambda_clamav_role.arn
  handler       = var.clamav_function_handler
  source_code_hash = "${data.aws_s3_bucket_object.clamav_function_hash.body}"
  runtime     = "java17"
  layers      = ["${aws_lambda_layer_version.aws-java-base-layer-terraform.arn}"]
  memory_size = var.memory_size
  timeout     = 30
  tags = {
    Name        = "${var.application}-clamav-handler-function-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  environment {
    variables = {
      ENVIRONMENT                  = "${var.env_full}"
      WFDM_DOCUMENT_API_URL        = "${var.document_api_url}"
      WFDM_DOCUMENT_TOKEN_URL      = "${var.document_token_url}"
      WFDM_INDEXING_LAMBDA_NAME    = aws_lambda_function.terraform_wfdm_indexing_function.function_name
      WFDM_SNS_VIRUS_ALERT         = var.virus_alert
      WFDM_DOCUMENT_SECRET_MANAGER = "${var.secret_manager_name}"
    }
  }
  depends_on = [
    aws_lambda_layer_version.aws-java-base-layer-terraform
  ]
}

data "aws_sqs_queue" "clamav_queue" {
  name = var.clamQueue
}

resource "aws_lambda_event_source_mapping" "index_initializer_mapping" {
  event_source_arn = aws_sqs_queue.queue.arn
  function_name    = aws_lambda_function.terraform_indexing_initializer_function.arn
}

resource "aws_lambda_event_source_mapping" "clamAV_queue_mapping" {
  event_source_arn = data.aws_sqs_queue.clamav_queue.arn
  function_name    = aws_lambda_function.lambda_clamav_handler.arn
}

#Create OpenSearch and related resources
#COMPONENTS FOR OPENSEARCH

data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

resource "aws_security_group" "es" {
  name        = "${var.application_lowercase}-elasticsearch-security-group-${var.env}"
  description = "Managed by Terraform"
  vpc_id      = data.aws_vpc.main_vpc.id

  ingress {
    from_port = 443
    to_port   = 443
    protocol  = "tcp"

    cidr_blocks = [
      var.private_subnet_block
    ]

  }
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

/*
resource "aws_iam_service_linked_role" "es" {
  aws_service_name = "es.amazonaws.com"
  custom_suffix = "${var.env}"
}
*/



resource "aws_elasticsearch_domain" "main_elasticsearch_domain" {
  domain_name = var.opensearchDomainName

  domain_endpoint_options {
    custom_endpoint                 = "${var.opensearchDomainName}.${var.domain}"
    custom_endpoint_certificate_arn = var.custom_endpoint_certificate_arn
    custom_endpoint_enabled         = true
    enforce_https                   = true
    tls_security_policy             = "Policy-Min-TLS-1-2-2019-07"
  }

  advanced_security_options {
    enabled = true
    internal_user_database_enabled = true
    master_user_options {
      master_user_name = var.opensearch_user
      master_user_password = var.opensearch_password
    }
  }

  elasticsearch_version = var.ElasticSearch_Version

  node_to_node_encryption {
    enabled = true
  }

  encrypt_at_rest {
    enabled = true
  }

  // Workaround to bypass bug in terraform, see https://github.com/hashicorp/terraform-provider-aws/issues/30205 for details
  dynamic "auto_tune_options" {
    for_each = var.opensearch_autotune == "ENABLED" ? [1] : []

    content {
      desired_state       = var.opensearch_autotune
      rollback_on_disable = "NO_ROLLBACK"
    }
  }

  cluster_config {
    dedicated_master_count   = var.master_node_instance_count
    dedicated_master_enabled = var.master_node_usage
    dedicated_master_type    = var.master_node_instance_type
    instance_count           = var.data_node_instance_count
    instance_type            = var.data_node_instance_type
    warm_count               = var.ultrawarm_node_instance_count
    warm_type                = var.ultrawarm_node_instance_type
  }

  ebs_options {
    ebs_enabled = "true"
    volume_size = var.ebs_volume_size
  }

  advanced_options = {
    "rest.action.multi.allow_explicit_index" = "true"
  }

  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }

  // REMOVED ACCESS POLICY - not needed when instead using fine-grained access

#   access_policies = <<CONFIG
# {
#   "Version": "2012-10-17",
#   "Statement": [
#     {
#       "Effect": "Allow",
#       "Principal": {
#         "AWS": "*"
#       },
#       "Action": "es:*",
#       "Resource": "arn:aws:es:ca-central-1:460053263286:domain/wf1-wfdm-opensearch-${var.env}/*"
#     }
#   ]
# }
# CONFIG
}

/*

resource "aws_elasticsearch_domain_policy" "main" {
  domain_name = aws_elasticsearch_domain.es.domain_name
  access_policies = <<POLICIES
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "es:*",
            "Principal": "*",
            "Effect": "Allow",
            "Resource": "${aws_elasticsearch_domain.es.arn}/*"
        }
    ]
}
POLICIES
}
*/

data "aws_route53_zone" "main_route53_zone" {
  name = var.main_route53_zone
}











//API GATEWAY RESOURCES

//API Gateway Role

resource "aws_iam_role" "api_gateway_integration_role" {
  name        = "${var.application}-sqs-api-gateway-role-${var.env}"
  description = "Role used for POST from api gateway to sqs queue"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
     {
       "Action": "sts:AssumeRole",
        "Principal": {
          "Service": "apigateway.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
     }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "api-gateway-role-sqs-policy-attachment" {
  role       = aws_iam_role.api_gateway_integration_role.name
  policy_arn = aws_iam_policy.wfdm-send-sqs-message-from-api.arn
}

resource "aws_iam_role_policy_attachment" "api-gateway-role-cloudwatch-push-attachement" {
  role       = aws_iam_role.api_gateway_integration_role.name
  policy_arn = data.aws_iam_policy.api-gateway-push-to-cloudwatch-policy.arn
}


resource "aws_api_gateway_rest_api" "sqs-api-gateway" {
  name        = "${var.application}-sqs-api-gateway-${var.env}"
  description = "POST records to SQS queue"
}

resource "aws_api_gateway_domain_name" "gateway_custom_domain" {
  domain_name              = "wf1-${var.application_lowercase}-sqs-api-${var.env_lowercase}.${var.domain}"
  regional_certificate_arn = var.custom_endpoint_certificate_arn
  endpoint_configuration {
    types = ["REGIONAL"]
  }

}

resource "aws_api_gateway_request_validator" "sqs-api-gateway-validator" {
  name                        = "queryValidator"
  rest_api_id                 = aws_api_gateway_rest_api.sqs-api-gateway.id
  validate_request_body       = false
  validate_request_parameters = true
}

resource "aws_api_gateway_method" "sqs-gateway-post-method" {
  rest_api_id   = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id   = aws_api_gateway_rest_api.sqs-api-gateway.root_resource_id
  http_method   = "ANY"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.proxy" = false
  }
}


resource "aws_api_gateway_integration" "api" {
  rest_api_id             = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id             = aws_api_gateway_rest_api.sqs-api-gateway.root_resource_id
  http_method             = aws_api_gateway_method.sqs-gateway-post-method.http_method
  credentials             = aws_iam_role.api_gateway_integration_role.arn
  type                    = "AWS"
  integration_http_method = "ANY"
  uri                     = "arn:aws:apigateway:${var.region}:sqs:path/${data.aws_caller_identity.current.account_id}/${aws_sqs_queue.queue.name}"

  request_parameters = {
    "integration.request.header.Content-Type" = "'application/x-www-form-urlencoded'"
  }

  # Request Template for passing Method, Body, QueryParameters and PathParams to SQS messages
  request_templates = {
    "application/json" = <<EOF
Action=SendMessage&MessageBody={
  "method": "$context.httpMethod",
  "body-json" : $input.json('$'),
  "queryParams": {
    #foreach($param in $input.params().querystring.keySet())
    "$param": "$util.escapeJavaScript($input.params().querystring.get($param))" #if($foreach.hasNext),#end
  #end
  },
  "pathParams": {
    #foreach($param in $input.params().path.keySet())
    "$param": "$util.escapeJavaScript($input.params().path.get($param))" #if($foreach.hasNext),#end
    #end
  }
}
EOF
  }
  depends_on = [
    aws_iam_role_policy_attachment.sqs-api-exec-role
  ]
}

resource "aws_api_gateway_rest_api_policy" "api-gateway-policy" {
  rest_api_id = aws_api_gateway_rest_api.sqs-api-gateway.id
  policy      = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "*"
            },
            "Action": "execute-api:Invoke",
            "Resource": [
              "${aws_api_gateway_rest_api.sqs-api-gateway.execution_arn}",
              "${aws_api_gateway_rest_api.sqs-api-gateway.execution_arn}/*"
            ]
        }
    ]
}
  EOF
}

resource "aws_api_gateway_method_response" "http200" {
  rest_api_id = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id = aws_api_gateway_rest_api.sqs-api-gateway.root_resource_id
  http_method = aws_api_gateway_method.sqs-gateway-post-method.http_method
  status_code = 200
}

resource "aws_api_gateway_integration_response" "http200" {
  rest_api_id       = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id       = aws_api_gateway_rest_api.sqs-api-gateway.root_resource_id
  http_method       = aws_api_gateway_method.sqs-gateway-post-method.http_method
  status_code       = aws_api_gateway_method_response.http200.status_code
  selection_pattern = "^2[0-9][0-9]" // regex pattern for any 200 message that comes back from SQS

  depends_on = [
    aws_api_gateway_integration.api
  ]
}

resource "aws_api_gateway_deployment" "sqs-api-gateway-deployment" {
  rest_api_id = aws_api_gateway_rest_api.sqs-api-gateway.id
  stage_name  = var.env
  depends_on = [
    aws_api_gateway_integration.api
  ]

  triggers = {
    redeployment = sha1(jsonencode(aws_api_gateway_rest_api.sqs-api-gateway.body))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_base_path_mapping" "api_gateway_base_path_mapping" {
  api_id      = aws_api_gateway_rest_api.sqs-api-gateway.id
  stage_name  = aws_api_gateway_deployment.sqs-api-gateway-deployment.stage_name
  domain_name = aws_api_gateway_domain_name.gateway_custom_domain.domain_name
}

resource "aws_route53_record" "sqs-invoke-api-record" {
  zone_id = data.aws_route53_zone.main_route53_zone.id
  name    = aws_api_gateway_domain_name.gateway_custom_domain.domain_name
  type    = "A"
  alias {
    evaluate_target_health = true
    name                   = aws_api_gateway_domain_name.gateway_custom_domain.regional_domain_name
    zone_id                = aws_api_gateway_domain_name.gateway_custom_domain.regional_zone_id
  }
}

resource "aws_route53_record" "sqs-url-correction-record" {
  zone_id = data.aws_route53_zone.main_route53_zone.id
  name    = "sqs.${var.region}.${var.application}-sqs-${var.env}.${var.domain}"
  type    = "CNAME"
  ttl     = 300
  records = [
    "sqs.${var.region}.amazonaws.com"
  ]
}

resource "aws_route53_record" "opensearch-custom-url-redirect" {
  zone_id = data.aws_route53_zone.main_route53_zone.id
  name    = "${var.opensearchDomainName}.${var.domain}"
  type    = "CNAME"
  ttl     = 300
  records = [
    aws_elasticsearch_domain.main_elasticsearch_domain.endpoint
  ]
}

resource "aws_sns_topic" "clamav_virus" {
  name = "${var.application}-clamav-virus-topic-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  delivery_policy = <<EOF
{
  "http": {
    "defaultHealthyRetryPolicy": {
      "minDelayTarget": 20,
      "maxDelayTarget": 20,
      "numRetries": 3,
      "numMaxDelayRetries": 0,
      "numNoDelayRetries": 0,
      "numMinDelayRetries": 0,
      "backoffFunction": "linear"
    },
    "disableSubscriptionOverrides": false
  }
}
  EOF
  policy          = <<EOF
  {
    "Version": "2008-10-17",
    "Id": "__default_policy_ID",
    "Statement": [
      {
        "Sid": "__default_statement_ID",
        "Effect": "Allow",
        "Principal": {
          "AWS": "*"
        },
        "Action": [
          "SNS:GetTopicAttributes",
          "SNS:SetTopicAttributes",
          "SNS:AddPermission",
          "SNS:RemovePermission",
          "SNS:DeleteTopic",
          "SNS:Subscribe",
          "SNS:ListSubscriptionsByTopic",
          "SNS:Publish"
        ],
        "Resource": "arn:aws:sns:ca-central-1:${data.aws_caller_identity.current.account_id}:WFDM_CLAMAV_EMAIL_NOTIFICATION",
        "Condition": {
          "StringEquals": {
            "AWS:SourceOwner": "${data.aws_caller_identity.current.account_id}"
          }
        }
      }
    ]
  }
  EOF
}

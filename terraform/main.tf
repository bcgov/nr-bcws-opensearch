terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "3.72.0"
    }
  }
  required_version = ">= 1.1.0"
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

# Creating IAM role so that Lambda service to assume the role and access other  AWS services. 

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
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_policy" "lambda_role_sqs_policy" {
  name   = "${var.application}-all-sqs-role-policy-${var.env}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "sqs:*"
      ],
      "Effect": "Allow",
      "Resource": "*"
    },
    {
      "Action": [
        "opensearch:*"
      ],
      "Effect":"Allow",
      "Resource":"*"
    },
    {
      "Action": [
        "EC2:*"
      ],
      "Effect":"Allow",
      "Resource":"*"
    },
    {
      "Action": [
        "secretsmanager:*"
      ],
      "Effect":"Allow",
      "Resource":"*"
    }
  ]
}
EOF
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
          "Service": "opensearch.amazonaws.com"
        },
        "Effect": "Allow",
        "Sid": ""
     }
   ]
}
EOF
}

resource "aws_iam_policy" "sqs-iam-policy" {
  name = "${var.application}-log-and-sqs-${var.env}"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:DescribeLogGroups",
          "logs:DescribeLogStreams",
          "logs:PutLogEvents",
          "logs:GetLogEvents",
          "logs:FilterLogEvents"
      ],
      "Resource": "*"
    },
    {
      "Sid": "VisualEditor0",
      "Effect": "Allow",
      "Action": [
          "sqs:DeleteMessage",
          "sqs:GetQueueUrl",
          "sqs:ChangeMessageVisibility",
          "sqs:UntagQueue",
          "sqs:ReceiveMessage",
          "sqs:SendMessage",
          "sqs:GetQueueAttributes",
          "sqs:ListQueueTags",
          "sqs:TagQueue",
          "sqs:ListDeadLetterSourceQueues",
          "sqs:PurgeQueue",
          "sqs:DeleteQueue",
          "sqs:CreateQueue",
          "sqs:SetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:ca-central-1:460053263286:${var.application}-sqs-queue-${var.env}"
  },
  {
      "Sid": "VisualEditor1",
      "Effect": "Allow",
      "Action": "sqs:ListQueues",
      "Resource": "*"
  }
  ]
}
EOF

}

resource "aws_iam_role_policy_attachment" "sqs-api-exec-role" {
  role       = aws_iam_role.opensearch_sqs_role.name
  policy_arn = aws_iam_policy.sqs-iam-policy.arn
}


# lambda policy
resource "aws_iam_policy" "iam_policy_for_lambda" {
  name = "${var.application}-lambda-invoke-policy-${var.env}"
  path = "/"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "LambdaPolicy",
        "Effect": "Allow",
        "Action": [
          "cloudwatch:PutMetricData",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ],
        "Resource": "*"
      }
    ]
  }
EOF
}


# IAM policy for logging from a lambda

resource "aws_iam_policy" "lambda_logging" {
  name        = "${var.application}-iam_policy_lambda_logging_function-${var.env}"
  path        = "/"
  description = "IAM policy for logging from a lambda"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*",
      "Effect": "Allow"
    }
  ]
}
EOF
}

# Policy Attachment on the role.

resource "aws_iam_role_policy_attachment" "policy_attach" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_sqs" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_role_sqs_policy.arn
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
  depends_on = [aws_iam_role.opensearch_sqs_role]
  visibility_timeout_seconds = var.visibilityTimeoutSeconds
  name       = "${var.application}-sqs-queue-${var.env}"
  
  

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
      "Principal": {
        "AWS": "arn:aws:iam::460053263286:root"
      },
      "Action": "sqs:*",
      "Resource": "arn:aws:sqs::*:*:${var.application}-sqs-queue-${var.env}"
    },
    {
      "Sid": "Stmt1640124883525",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::460053263286:role/${aws_iam_role.opensearch_sqs_role.name}"
      },
      "Action": [
        "sqs:ListDeadLetterSourceQueues",
        "sqs:ListQueueTags",
        "sqs:ListQueues",
        "sqs:ReceiveMessage",
        "sqs:SendMessage",
        "sqs:GetQueueAttributes",
        "sqs:DeleteMessage"


      ],
      "Resource": "arn:aws:sqs:ca-central-1:460053263286:${var.application}-sqs-queue-${var.env}"
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
resource "aws_s3_bucket" "terraform-s3-bucket" {
  bucket = "${var.s3BucketName}"
  acl    = "private"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_s3_bucket" "clamav-bucket" {
  bucket = "${var.clamAVBucketName}"
  acl = "private"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}

resource "aws_s3_bucket_policy" "terraform-s3-bucket-policy" {
  bucket = aws_s3_bucket.terraform-s3-bucket.id
  policy = data.aws_iam_policy_document.s3-bucket-policy.json
}

resource "aws_s3_bucket_policy" "terraform-clamav-bucket-policy" {
  bucket = aws_s3_bucket.clamav-bucket.id
  policy = data.aws_iam_policy_document.s3-bucket-policy.json
}

data "aws_iam_policy_document" "s3-bucket-policy" {

  statement {
    principals {
      type        = "AWS"
      identifiers = ["${aws_iam_role.s3-bucket-add-remove-role.arn}"]
    }
    effect = "Allow"
    actions = [
      "s3:Get*",
      "s3:List*",
      "s3:DeleteObject*",
      "s3:Put*"
    ]
    resources = [
      "${aws_s3_bucket.terraform-s3-bucket.arn}",
      "${aws_s3_bucket.terraform-s3-bucket.arn}/*"
    ]
  }

  statement {
    effect = "Deny"
    not_principals {
      type        = "AWS"
      identifiers = ["${aws_iam_role.s3-clamav-bucket-role.arn}"]
    }
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.terraform-s3-bucket.arn}",
      "${aws_s3_bucket.terraform-s3-bucket.arn}/*"
    ]
    condition {
      test     = "StringEquals"
      variable = "s3:ExistingObjectTag/scan-status"
      values = [
        "IN PROGRESS",
        "INFECTED",
        "ERROR"
      ]
    }
  }
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


#Upload java.zip to s3bucket
/*
resource "aws_s3_bucket_object" "java_zip" {
  bucket = aws_s3_bucket.terraform-s3-bucket.id
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
  bucket = aws_s3_bucket.terraform-s3-bucket.bucket
  key = var.layer_file_name
}

data "aws_s3_bucket_object" "s3_lambda_payload_object" {
  bucket = aws_s3_bucket.terraform-s3-bucket.bucket
  key = var.lambda_payload_filename
}



resource "aws_lambda_layer_version" "aws-java-base-layer-terraform" {
  layer_name          = "${var.application}-${var.java_layer_name}-${var.env}"
  s3_bucket           = aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key              = var.layer_file_name
  description         = "Common layer with java jars files"
  compatible_runtimes = ["java8"]
  skip_destroy        = true

}



#Lambda Function Handler
resource "aws_lambda_function" "terraform_wfdm_indexing_function" {
  function_name    = "${var.application}-indexing-function-${var.env}"
  s3_bucket = aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key = var.lambda_payload_filename
  role             = aws_iam_role.lambda_role.arn
  handler          = var.lambda_function_handler
  //source_code_hash = filebase64sha256(aws_s3_bucket_object.s3_lambda_payload_object)
  runtime          = "java8"
  layers           = ["${aws_lambda_layer_version.aws-java-base-layer-terraform.arn}"]
  /*
  vpc_config {
    subnet_ids = [aws_subnet.private_subnet.id]
    security_group_ids = [aws_vpc.main_vpc.default_security_group_id]
  }
  */
  tags = {
    Name        = "${var.application}-indexing-function-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  environment {
    variables = {
      ENVIRONMENT = "${var.env_full}"
      WFDM_DOCUMENT_API_URL = "${var.document_api_url}"
      WFDM_DOCUMENT_CLAMAV_S3BUCKET	= aws_s3_bucket.clamav-bucket.arn
      WFDM_DOCUMENT_OPENSEARCH_DOMAIN_ENDPOINT = aws_elasticsearch_domain.main_elasticsearch_domain.endpoint
      WFDM_DOCUMENT_OPENSEARCH_INDEXNAME = aws_elasticsearch_domain.main_elasticsearch_domain.domain_name
      WFDM_DOCUMENT_TOKEN_URL = "${var.document_token_url}"
    }
  }
}

#Lambda File Indexing Initializer
resource "aws_lambda_function" "terraform_indexing_initializer_function" {
  function_name    = "${var.application}-indexing-initializer-${var.env}"
  s3_bucket = aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key = var.lambda_initializer_filename
  role             = aws_iam_role.lambda_role.arn
  handler = var.indexing_function_handler
  //source_code_hash = filebase64sha256(aws_s3_bucket_object.s3_lambda_payload_object)
  runtime          = "java8"
  layers           = ["${aws_lambda_layer_version.aws-java-base-layer-terraform.arn}"]
  tags = {
    Name        = "${var.application}-indexing-initializer-function-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  environment {
    variables = {
      ENVIRONMENT = "${var.env_full}"
      WFDM_DOCUMENT_API_URL = "${var.document_api_url}"
      WFDM_DOCUMENT_CLAMAV_S3BUCKET	= aws_s3_bucket.clamav-bucket.arn
      WFDM_DOCUMENT_TOKEN_URL = "${var.document_token_url}"
      WFDM_INDEXING_LAMBDA_NAME = aws_lambda_function.terraform_wfdm_indexing_function.function_name
    }
  }
}

resource "aws_lambda_event_source_mapping" "index_initializer_mapping" {
  event_source_arn = aws_sqs_queue.queue.arn
  function_name = aws_lambda_function.terraform_indexing_initializer_function.arn
}

#Create OpenSearch and related resources
#COMPONENTS FOR OPENSEARCH

data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

resource "aws_security_group" "es" {
  name        = "${var.application_lowercase}-elasticsearch-security-group-${var.env}"
  description = "Managed by Terraform"
  vpc_id      = aws_vpc.main_vpc.id

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
  domain_name           = "${var.opensearchDomainName}"

  elasticsearch_version = var.ElasticSearch_Version

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

/*
  vpc_options {
    subnet_ids = [
      aws_subnet.public_subnet.id
    ]
    security_group_ids = [aws_security_group.es.id]
  }
*/

  advanced_options = {
    "rest.action.multi.allow_explicit_index" = "true"
  }

  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }

  access_policies = <<CONFIG
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "es:*",
            "Principal": "${data.aws_caller_identity.current.account_id}",
            "Effect": "Allow",
            "Resource": "arn:aws:es:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:domain/${var.opensearchDomainName}/*"
        }
    ]
}
CONFIG
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

resource "aws_api_gateway_rest_api" "sqs-api-gateway" {
  name        = "${var.application}-sqs-api-gateway-${var.env}"
  description = "POST records to SQS queue"
}

resource "aws_api_gateway_resource" "sqs-api-gateway-resource" {
  rest_api_id = aws_api_gateway_rest_api.sqs-api-gateway.id
  parent_id   = aws_api_gateway_rest_api.sqs-api-gateway.root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_request_validator" "sqs-api-gateway-validator" {
  name                        = "queryValidator"
  rest_api_id                 = aws_api_gateway_rest_api.sqs-api-gateway.id
  validate_request_body       = false
  validate_request_parameters = true
}

resource "aws_api_gateway_method" "sqs-gateway-post-method" {
  rest_api_id   = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id   = aws_api_gateway_resource.sqs-api-gateway-resource.id
  http_method   = "POST"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.proxy"        = false
    "method.request.querystring.unity" = true
  }
  request_validator_id = aws_api_gateway_request_validator.sqs-api-gateway-validator.id
}


resource "aws_api_gateway_integration" "api" {
  rest_api_id             = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id             = aws_api_gateway_resource.sqs-api-gateway-resource.id
  http_method             = aws_api_gateway_method.sqs-gateway-post-method.http_method
  type                    = "AWS"
  integration_http_method = "POST"
  credentials             = aws_iam_role.opensearch_sqs_role.arn
  uri                     = "arn:aws:apigateway:${var.region}:sqs:path/${aws_sqs_queue.queue.name}"

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

resource "aws_api_gateway_method_response" "http200" {
  rest_api_id = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id = aws_api_gateway_resource.sqs-api-gateway-resource.id
  http_method = aws_api_gateway_method.sqs-gateway-post-method.http_method
  status_code = 200
}

resource "aws_api_gateway_integration_response" "http200" {
  rest_api_id       = aws_api_gateway_rest_api.sqs-api-gateway.id
  resource_id       = aws_api_gateway_resource.sqs-api-gateway-resource.id
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

/*
resource "aws_route53_record" "sqs-route53-record" {
  zone_id = data.aws_route53_zone.main_route53_zone.id
  name    = "${var.application}-sqs-${var.env}.${var.domain}"
  type    = "A"
  ttl     = 300
  records = [
    "${aws_api_gateway_deployment.sqs-api-gateway-deployment.invoke_url}"
  ]
}
*/


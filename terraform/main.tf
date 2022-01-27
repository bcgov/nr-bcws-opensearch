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
  subnet_id = aws_subnet.private_subnet.id
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
 name   = "${var.application}-iam_role_lambda_index_searching-${var.env}"
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
  name = "${var.application}-all-sqs-role-policy-${var.env}"
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
    }
  ]
}
EOF
}

resource "aws_iam_role" "opensearch_sqs_role" {
 name   = "${var.application}-iam-role-opensearch-sqs-${var.env}"
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

resource "aws_iam_policy" "iam_policy_for_opensearch" {
  name = "${var.application}-opensearch-sqs-${var.env}"
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
  name         = "${var.application}-iam_policy_lambda_logging_function-${var.env}"
  path         = "/"
  description  = "IAM policy for logging from a lambda"
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
  role        = aws_iam_role.lambda_role.name
  policy_arn  = aws_iam_policy.lambda_logging.arn
}

resource "aws_iam_role_policy_attachment" "policy_attach_sqs" {
  role = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_role_sqs_policy.arn
}



# SQS queue

resource "aws_sqs_queue" "queue" {
  depends_on = [aws_iam_role.opensearch_sqs_role]
  name = "${var.application}-sqs-queue-${var.env}"

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
  depends-on = [aws_iam_role.s3-bucket-add-remove-role, aws_iam_role.s3-clamav-bucket-role]
  bucket = "${var.application}-s3-bucket-${var.env}"
  acl    = "private"
  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
  policy = <<POLICY
  {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Principal": {
                "AWS": "${aws_iam_role.s3-bucket-add-remove-role.arn}"
            },
            "Effect": "Allow",
            "Action": [
                "s3:Get*",
                "s3:List*",
                "s3:DeleteObject*",
                "s3:Put*"
            ]
        },
        {
            "Effect": "Deny",
            "NotPrincipal": {
                "AWS": [
                    "${aws_iam_role.s3-clamav-bucket-role.arn}"
                ]
            },
            "Action": "s3:GetObject",
            "Condition": {
                "StringEquals": {
                    "s3:ExistingObjectTag/scan-status": [
                        "IN PROGRESS",
                        "INFECTED",
                        "ERROR"
                    ]
                }
            }
        }
    ]
}
POLICY
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

resource "aws_iam_policy" "s3-bucket-add-remove-policy" {
  name = "${var.application}-s3-bucket-add-remove-policy-${var.env}"
  policy = <<POLICY
  {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Principal": {
                "AWS": "${aws_iam_role.s3-bucket-add-remove-role.arn}"
            },
            "Effect": "Allow",
            "Action": [
                "s3:Get*",
                "s3:List*",
                "s3:DeleteObject*",
                "s3:Put*"
            ],
            "Resource": [
                "${aws_s3_bucket.terraform-s3-bucket.arn}",
                "${aws_s3_bucket.terraform-s3-bucket.arn}/*"
            ]
        },
        {
            "Effect": "Deny",
            "NotPrincipal": {
                "AWS": [
                    "${aws_iam_role.s3-clamav-bucket-role.arn}"
                ]
            },
            "Action": "s3:GetObject",
            "Resource": "${aws_s3_bucket.terraform-s3-bucket.arn}/*",
            "Condition": {
                "StringEquals": {
                    "s3:ExistingObjectTag/scan-status": [
                        "IN PROGRESS",
                        "INFECTED",
                        "ERROR"
                    ]
                }
            }
        }
    ]
}
POLICY
}



/* 
#Upload java.zip to s3bucket
resource "aws_s3_bucket_object" "java_zip" {
  bucket       = aws_s3_bucket.terraform-s3-bucket.id
  key          = "${var.layer_file_name}"
  acl          = "private" 
  source       = "aws-lambda-layer-base/java.zip"
  tags = {
    Name        = "${var.application}-s3-bucket-object-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}


resource "aws_lambda_layer_version" "aws-java-base-layer-terraform" {
  layer_name = "${var.java_layer_name}"
  s3_bucket = aws_s3_bucket.terraform-s3-bucket.bucket
  s3_key = var.layer_file_name
  description = "Common layer with java jars files"
  compatible_runtimes = ["java8"]
  skip_destroy        = true

}



#Lambda Function Handler

resource "aws_lambda_function" "terraform_wfdm_indexing_function" {
  function_name = "terraform-wfdm-indexing-function"
  filename      = "${var.lambda_payload_filename}"
  role             = aws_iam_role.lambda_role.arn
  handler          = "${var.lambda_function_handler}"
  source_code_hash = "${filebase64sha256(var.lambda_payload_filename)}"
  runtime          = "java8"
  layers = ["${aws_lambda_layer_version.aws-java-base-layer-terraform.arn}"]
  tags = {
    Name        = "${var.application}-wfdm-indexing-function-${var.env}"
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
 
}


*/
#Create OpenSearch and related resources
#COMPONENTS FOR OPENSEARCH

data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

resource "aws_security_group" "es" {
  name        = "${var.application}-elasticsearch-security-group-${var.env}"
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
  domain_name           = "${var.application}-opensearch-${var.env}"
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

  vpc_options {
    subnet_ids = [
      aws_subnet.public_subnet.id
    ]

    security_group_ids = [aws_security_group.es.id]
  }

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
            "Principal": "*",
            "Effect": "Allow",
            "Resource": "arn:aws:es:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:domain/${var.application}-${var.tool}-${var.env}/*"
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
  name = "${var.main_route53_zone}"
}

resource "aws_api_gateway_rest_api" "sqs-api-gateway" {
  body = jsonencode({
    openapi = "3.0.1"
    info = {
      title   = "example"
      version = "1.0"
    }
    paths = {
      "/" = {
        any = {
          x-amazon-apigateway-integration = {
            httpMethod           = "ANY"
            payloadFormatVersion = "1.0"
            type                 = "AWS"
            uri                  = "${aws_sqs_queue.queue.arn}"
          }
        }
      }
    }
  })

  name = "${var.application}-sqs-api-gateway-${var.env}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_deployment" "sqs-api-gateway-deployment" {
  rest_api_id = aws_api_gateway_rest_api.sqs-api-gateway.id

  triggers = {
    redeployment = sha1(jsonencode(aws_api_gateway_rest_api.sqs-api-gateway.body))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "sqs-api-gateway-stage" {
  deployment_id = aws_api_gateway_deployment.sqs-api-gateway-deployment.id
  rest_api_id   = aws_api_gateway_rest_api.sqs-api-gateway.id
  stage_name    = "${var.application}-sqs-api-gateway-stage-${var.env}"
}

resource "aws_route53_record" "sqs-route53-record"{
  zone_id = data.aws_route53_zone.main_route53_zone.id
  name = "${var.application}-sqs-${var.env}.${var.domain}"
  type="A"
  ttl=300
  records=[
    "${aws_api_gateway_stage.sqs-api-gateway-stage.invoke_url}"
  ]
}

resource "aws_api_gateway_vpc_link" "vpc-opensearch-api-link" {
  name = "${var.application}-api-gateway-vpc-link-${var.env}"
  description = "Make the opensearch REST api accessible through the VPC"
  target_arns = [aws_elasticsearch_domain.main_elasticsearch_domain.arn]

  tags = {
    Application = var.application
    Customer    = var.customer
    Environment = var.env
  }
}



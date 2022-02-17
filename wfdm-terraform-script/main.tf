# Creating IAM role so that Lambda service to assume the role and access other  AWS services. 
 
resource "aws_iam_role" "lambda_role" {
 name   = "iam_role_lambda_index_searching"
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
    },
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


# lambda policy
resource "aws_iam_policy" "iam_policy_for_lambda" {
  name = "lambda-invoke-policy"
  path = "/"

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
  name         = "iam_policy_lambda_logging_function"
  path         = "/"
  description  = "IAM policy for logging from a lambda"
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



# SQS queue

resource "aws_sqs_queue" "queue" {
  name = var.queue_name

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
      "Resource": "arn:aws:sqs::*:*:var.queue_name"
    },
    {
      "Sid": "Stmt1640124883525",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::460053263286:role/wfdm-opensearch-sqs-dev-role"
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
      "Resource": "arn:aws:sqs:ca-central-1:460053263286:wfdm-index-searching-queue"
    }
  ]
}
POLICY
}



# Event source from SQS
resource "aws_lambda_event_source_mapping" "event_source_mapping" {
  event_source_arn = aws_sqs_queue.queue.arn
  enabled          = true
  function_name    = aws_lambda_function.terraform_wfdm_indexing_function.arn
  batch_size       = 1
}


#Create s3 bucket
resource "aws_s3_bucket" "terraform-s3-bucket" {
  bucket = var.s3_bucket_name
  acl    = "private"

}

#Upload java.zip to s3bucket
resource "aws_s3_bucket_object" "java_zip" {
  bucket       = aws_s3_bucket.terraform-s3-bucket.id
  key          = var.layer_file_name
  acl          = "private" 
  source       = "aws-lambda-layer-base/java.zip"
}


resource "aws_lambda_layer_version" "aws-java-base-layer-terraform" {
  layer_name = var.layer_name
  s3_bucket = var.s3_bucket_name
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
 
}

#Create Openseach
resource "aws_elasticsearch_domain" "es" {
  domain_name           = var.domain
  elasticsearch_version = "OpenSearch_1.0"

  cluster_config {
    instance_type = var.instance_type
  }
  snapshot_options {
    automated_snapshot_start_hour = 23
  }
  vpc_options {
    subnet_ids = ["subnet-09f043b74e40907c0"] 
  }
  ebs_options {
    ebs_enabled = var.ebs_volume_size > 0 ? true : false
    volume_size = var.ebs_volume_size
    volume_type = var.volume_type
  }
  tags = {
    Domain = var.tag_domain
  }
}


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




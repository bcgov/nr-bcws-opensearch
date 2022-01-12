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
        "sqs:SendMessage"
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



resource "aws_lambda_layer_version" "aws-java-base-layer-terraform" {
  layer_name = var.layer_name
  s3_bucket = "aws-lambda-layer-base"
  s3_key = "java.zip"
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





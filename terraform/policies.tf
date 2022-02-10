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
    },
    {
      "Action": [
        "s3:*"
      ],
      "Effect":"Allow",
      "Resource":"*"
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

resource "aws_iam_policy" "wfdm-send-sqs-message-from-api" {
  name = "${var.application}-sqs-send-message-${var.env}"
  policy = <<POLICY
  {
    "Version": "2012-10-17",
    "Statement": [
        {
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
            "Resource": "${aws_sqs_queue.queue.arn}"
        },
        {
            "Effect": "Allow",
            "Action": "sqs:ListQueues",
            "Resource": "*"
        }
    ]
}
  POLICY
}

data "aws_iam_policy" "api-gateway-push-to-cloudwatch-policy" {
  arn = "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
}

data "aws_iam_policy" "s3-full-access-policy" {
    arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

data "aws_iam_policy" "lambda-vpc-access-execution-role" {
    arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}
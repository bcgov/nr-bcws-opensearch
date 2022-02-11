resource "aws_iam_policy" "clamav-s3-permission" {
  name = "${var.application}-clamav-s3-permission-${var.env}"
  policy = <<POLICY
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "s3:*",
            "Resource": "*"
        }
    ]
}
  POLICY
}

resource "aws_iam_policy" "elasticsearch-access" {
  name = "${var.application}-es-permission-${var.env}"
  policy = <<POLICY
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Action": "es:*",
            "Resource": "*"
        }
    ]
}
  POLICY
}

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

resource "aws_iam_policy" "sns-publish" {
  {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Action": [
                "sns:DeleteTopic",
                "sns:ListTopics",
                "sns:Unsubscribe",
                "sns:CreatePlatformEndpoint",
                "sns:SetTopicAttributes",
                "sns:OptInPhoneNumber",
                "sns:CheckIfPhoneNumberIsOptedOut",
                "sns:ListEndpointsByPlatformApplication",
                "sns:SetEndpointAttributes",
                "sns:Publish",
                "sns:DeletePlatformApplication",
                "sns:SetPlatformApplicationAttributes",
                "sns:VerifySMSSandboxPhoneNumber",
                "sns:Subscribe",
                "sns:ConfirmSubscription",
                "sns:ListTagsForResource",
                "sns:DeleteSMSSandboxPhoneNumber",
                "sns:ListSubscriptionsByTopic",
                "sns:GetTopicAttributes",
                "sns:ListSMSSandboxPhoneNumbers",
                "sns:CreatePlatformApplication",
                "sns:SetSMSAttributes",
                "sns:CreateTopic",
                "sns:GetPlatformApplicationAttributes",
                "sns:GetSubscriptionAttributes",
                "sns:ListSubscriptions",
                "sns:ListOriginationNumbers",
                "sns:DeleteEndpoint",
                "sns:ListPhoneNumbersOptedOut",
                "sns:GetEndpointAttributes",
                "sns:SetSubscriptionAttributes",
                "sns:GetSMSSandboxAccountStatus",
                "sns:CreateSMSSandboxPhoneNumber",
                "sns:ListPlatformApplications",
                "sns:GetSMSAttributes"
            ],
            "Resource": "*"
        }
    ]
  }
}

resource "aws_iam_policy" "sqs-lambda-permission" {
  name = "${var.application}-sqs-lambda-permission-${var.env}"
  policy = <<POLICY
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Action": "sqs:*",
            "Resource": "*"
        }
    ]
}
  POLICY
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



data "aws_iam_policy" "api-gateway-push-to-cloudwatch-policy" {
  arn = "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
}

data "aws_iam_policy" "lambda-execution" {
  arn = "arn:aws:iam::aws:policy/AWSLambdaExecute"
}

data "aws_iam_policy" "lambda-full-access" {
  arn = "arn:aws:iam::aws:policy/AWSLambda_FullAccess"
}

data "aws_iam_policy" "lambda-role" {
  arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaRole"
}

data "aws_iam_policy" "lambda-vpc-access-execution" {
    arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy" "s3-full-access-policy" {
    arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

data "aws_iam_policy" "sqs-full-access-policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSQSFullAccess"
}

data "aws_iam_policy" "secretsmanager-readwrite" {
  arn = "arn:aws:iam::aws:policy/SecretsManagerReadWrite"
}


provider "aws" {
  region = "us-east-1"
}

resource "aws_sqs_queue" "payouts_dlq" {
  name = "payouts-dlq"
}

resource "aws_sqs_queue" "payouts_queue" {
  name                      = "payouts-queue"
  message_retention_seconds = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.payouts_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_cloudwatch_metric_alarm" "payouts_queue_age_alarm" {
  alarm_name          = "payouts-queue-high-age"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = "60"
  statistic           = "Maximum"
  threshold           = 300
  alarm_description   = "Alerts when payout messages are stuck in the queue for more than 5 minutes."
  alarm_actions       = [] 
  
  dimensions = {
    QueueName = aws_sqs_queue.payouts_queue.name
  }
}

resource "aws_iam_policy" "payouts_worker_policy" {
  name        = "payouts-worker-sqs-policy"
  description = "Policy for payouts worker to process SQS messages"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = aws_sqs_queue.payouts_queue.arn
      }
    ]
  })
}

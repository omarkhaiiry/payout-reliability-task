provider "aws" {
  region = "us-east-1"
}

# Dead Letter Queue (DLQ)
# Stores messages that fail processing repeatedly to allow for investigation.
resource "aws_sqs_queue" "payouts_dlq" {
  name = "payouts-dlq"
}

# Main Payouts Queue
resource "aws_sqs_queue" "payouts_queue" {
  name                      = "payouts-queue"
  message_retention_seconds = 86400 # 1 day retention

  # Redrive Policy
  # We use a maxReceiveCount of 5.
  # This is chosen to prevent poison-pill messages (malformed data that always crashes the consumer)
  # from blocking the consumer indefinitely. After 5 retries, the message moves to the DLQ.
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.payouts_dlq.arn
    maxReceiveCount     = 5
  })
}

# CloudWatch Alarm: Consumer Liveness Check
# Monitors the age of the oldest message in the queue.
resource "aws_cloudwatch_metric_alarm" "payouts_queue_age_alarm" {
  alarm_name          = "payouts-queue-high-age"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = "60"
  statistic           = "Maximum"
  
  # Threshold: 300 seconds (5 minutes)
  # Explanation: Payouts should typically process in sub-seconds. 
  # If messages are sitting for 5 minutes, it implies the consumer is likely stuck, 
  # crashed, or failing to process messages faster than the arrival rate.
  threshold           = 300
  
  alarm_description   = "Alerts when payout messages are stuck in the queue for more than 5 minutes."
  alarm_actions       = [] # e.g., SNS topic ARN for PagerDuty
  
  dimensions = {
    QueueName = aws_sqs_queue.payouts_queue.name
  }
}

# IAM Policy for Worker
# Minimal permissions required for the worker to process messages.
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

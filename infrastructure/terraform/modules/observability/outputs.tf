output "alarm_names" {
  description = "Names of all alarms created."
  value = concat(
    [aws_cloudwatch_metric_alarm.alb_5xx.alarm_name, aws_cloudwatch_metric_alarm.rds_free_storage.alarm_name, aws_cloudwatch_metric_alarm.rds_cpu.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.ecs_cpu : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.ecs_memory : a.alarm_name],
  )
}

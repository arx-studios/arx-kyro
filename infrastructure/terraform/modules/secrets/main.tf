# Containers only. No aws_secretsmanager_secret_version resources: values never enter Terraform state or git.
resource "aws_secretsmanager_secret" "this" {
  for_each = var.secret_names

  name                    = "${var.name}/${each.key}"
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = var.recovery_window_in_days
  description             = "KYRO ${var.name} ${each.key}. Value is set out of band."
}

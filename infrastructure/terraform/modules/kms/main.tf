# Account-root key policy: access is then granted through IAM policies of the consuming roles.
data "aws_iam_policy_document" "key" {
  statement {
    sid       = "EnableIamPolicies"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${var.account_id}:root"]
    }
  }
}

resource "aws_kms_key" "this" {
  for_each = var.purposes

  description             = "${var.name} ${each.key}"
  enable_key_rotation     = true
  deletion_window_in_days = var.deletion_window_in_days
  policy                  = data.aws_iam_policy_document.key.json

  tags = { Purpose = each.key }
}

resource "aws_kms_alias" "this" {
  for_each = var.purposes

  name          = "alias/${var.name}-${each.key}"
  target_key_id = aws_kms_key.this[each.key].key_id
}

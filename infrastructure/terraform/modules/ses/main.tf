resource "aws_sesv2_email_identity" "this" {
  count = var.enable_ses ? 1 : 0

  email_identity = var.domain

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }

  lifecycle {
    precondition {
      condition     = var.domain != ""
      error_message = "domain must be set when enable_ses is true."
    }
  }
}

#trivy:ignore:AVD-AWS-0089 Server access logging is deferred: CloudTrail data events and S3 versioning cover staging; accepted for Phase 5 (see runbook).
resource "aws_s3_bucket" "this" {
  bucket = var.bucket_name
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_versioning" "this" {
  bucket = aws_s3_bucket.this.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "expire-noncurrent-and-abort-multipart"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_expiration_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

resource "aws_s3_bucket_cors_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  cors_rule {
    allowed_origins = [var.web_origin]
    allowed_methods = ["PUT"]
    allowed_headers = ["content-type", "content-length", "x-amz-*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 600
  }
}

data "aws_iam_policy_document" "bucket" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.this.arn, "${aws_s3_bucket.this.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  # Uploads that name an encryption scheme must name aws:kms. Uploads that omit the header get the bucket default (SSE-KMS).
  statement {
    sid       = "DenyNonKmsEncryptedPuts"
    effect    = "Deny"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.this.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "StringNotEqualsIfExists"
      variable = "s3:x-amz-server-side-encryption"
      values   = ["aws:kms"]
    }
  }

  statement {
    sid       = "DenyWrongKmsKey"
    effect    = "Deny"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.this.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "StringNotEqualsIfExists"
      variable = "s3:x-amz-server-side-encryption-aws-kms-key-id"
      values   = [var.kms_key_arn]
    }
  }
}

resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.bucket.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}

# GuardDuty Malware Protection for S3: scans new objects and tags them. App gating on the tag is Phase 6.
data "aws_iam_policy_document" "malware_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["malware-protection-plan.guardduty.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "malware" {
  statement {
    sid       = "ManageEventBridgeRules"
    actions   = ["events:PutRule", "events:DeleteRule", "events:PutTargets", "events:RemoveTargets"]
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "events:ManagedBy"
      values   = ["malware-protection-plan.guardduty.amazonaws.com"]
    }
  }

  statement {
    sid       = "DescribeEventBridgeRules"
    actions   = ["events:DescribeRule", "events:ListTargetsByRule"]
    resources = ["*"]
  }

  statement {
    sid       = "ReadBucketForScan"
    actions   = ["s3:GetBucketNotification", "s3:PutBucketNotification", "s3:GetBucketLocation", "s3:ListBucket"]
    resources = [aws_s3_bucket.this.arn]
  }

  statement {
    sid       = "ReadAndTagObjects"
    actions   = ["s3:GetObject", "s3:GetObjectVersion", "s3:GetObjectTagging", "s3:GetObjectVersionTagging", "s3:PutObjectTagging", "s3:PutObjectVersionTagging"]
    resources = ["${aws_s3_bucket.this.arn}/*"]
  }

  statement {
    sid       = "DecryptObjects"
    actions   = ["kms:GenerateDataKey", "kms:Decrypt"]
    resources = [var.kms_key_arn]
  }
}

resource "aws_iam_role" "malware" {
  count = var.enable_malware_protection ? 1 : 0

  name               = "${var.bucket_name}-guardduty-malware"
  assume_role_policy = data.aws_iam_policy_document.malware_assume.json
}

resource "aws_iam_role_policy" "malware" {
  count = var.enable_malware_protection ? 1 : 0

  name   = "scan-and-tag"
  role   = aws_iam_role.malware[0].id
  policy = data.aws_iam_policy_document.malware.json
}

resource "aws_guardduty_malware_protection_plan" "this" {
  count = var.enable_malware_protection ? 1 : 0

  role = aws_iam_role.malware[0].arn

  protected_resource {
    s3_bucket {
      bucket_name = aws_s3_bucket.this.id
    }
  }

  actions {
    tagging {
      status = "ENABLED"
    }
  }

  depends_on = [aws_iam_role_policy.malware]
}

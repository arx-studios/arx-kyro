terraform {
  # 1.10+ for S3-native state locking (use_lockfile), no DynamoDB table needed.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Partial configuration: supply the real values with
  #   terraform init -backend-config=backend.hcl        (see backend.hcl.example)
  # or run `terraform init -backend=false` for validation without any AWS access.
  backend "s3" {}
}

provider "aws" {
  region = var.region

  # Offline plan in CI only (no credentials, no network calls to AWS): -var offline_plan=true
  skip_credentials_validation = var.offline_plan
  skip_requesting_account_id  = var.offline_plan
  skip_metadata_api_check     = var.offline_plan
  skip_region_validation      = var.offline_plan

  default_tags {
    tags = local.tags
  }
}

# CloudFront and CLOUDFRONT-scope WAF resources live in us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  skip_credentials_validation = var.offline_plan
  skip_requesting_account_id  = var.offline_plan
  skip_metadata_api_check     = var.offline_plan
  skip_region_validation      = var.offline_plan

  default_tags {
    tags = local.tags
  }
}

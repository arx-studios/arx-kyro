terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# The caller passes the us-east-1 provider as the default (aws) provider of this module:
#   providers = { aws = aws.us_east_1 }
# CloudFront and WAFv2 CLOUDFRONT-scope resources must live in us-east-1.

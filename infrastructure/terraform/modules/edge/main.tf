locals {
  # AWS-managed CloudFront policies (global constants, no lookup needed).
  cache_policy_caching_disabled    = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  origin_request_policy_all_viewer = "216adef6-5c7f-47e4-b989-5492eafa07d3"

  managed_rule_groups = {
    common     = { name = "AWSManagedRulesCommonRuleSet", priority = 20 }
    bad_inputs = { name = "AWSManagedRulesKnownBadInputsRuleSet", priority = 30 }
    sqli       = { name = "AWSManagedRulesSQLiRuleSet", priority = 40 }
  }
}

resource "aws_wafv2_ip_set" "admin_allowed" {
  name               = "${var.name}-admin-allowed"
  description        = "IPv4 addresses allowed to reach the admin hostname"
  scope              = "CLOUDFRONT"
  ip_address_version = "IPV4"
  addresses          = var.admin_allowed_cidrs
}

resource "aws_wafv2_web_acl" "this" {
  name        = "${var.name}-edge"
  description = "Edge protection for ${var.name}"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  # Admin hostname: block everyone outside admin_allowed_cidrs.
  rule {
    name     = "admin-host-ip-allowlist"
    priority = 10

    action {
      block {}
    }

    statement {
      and_statement {
        statement {
          byte_match_statement {
            search_string         = lower(var.admin_host)
            positional_constraint = "EXACTLY"

            field_to_match {
              single_header {
                name = "host"
              }
            }

            text_transformation {
              priority = 0
              type     = "LOWERCASE"
            }
          }
        }

        statement {
          not_statement {
            statement {
              ip_set_reference_statement {
                arn = aws_wafv2_ip_set.admin_allowed.arn
              }
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-admin-host-ip-allowlist"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = local.managed_rule_groups

    content {
      name     = rule.value.name
      priority = rule.value.priority

      override_action {
        none {}
      }

      statement {
        managed_rule_group_statement {
          name        = rule.value.name
          vendor_name = "AWS"
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${var.name}-${rule.key}"
        sampled_requests_enabled   = true
      }
    }
  }

  rule {
    name     = "rate-limit-per-ip"
    priority = 50

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.rate_limit_per_5_minutes
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-edge"
    sampled_requests_enabled   = true
  }
}

#trivy:ignore:AVD-AWS-0010 Access logging deferred for staging (needs a dedicated log bucket); WAF sampled requests and ALB metrics cover Phase 5.
resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.name} web, admin and api"
  aliases         = [var.web_host, var.admin_host]
  price_class     = "PriceClass_100"
  http_version    = "http2and3"
  web_acl_id      = aws_wafv2_web_acl.this.arn

  origin {
    origin_id   = "alb"
    domain_name = var.origin_domain_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id         = "alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = local.cache_policy_caching_disabled
    origin_request_policy_id = local.origin_request_policy_all_viewer
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.cloudfront_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

locals {
  name = "kyro-staging"

  tags = {
    Project     = "kyro"
    Environment = "staging"
    ManagedBy   = "terraform"
  }

  web_origin   = "https://${var.web_host}"
  api_origin   = "http://api:8080" # Service Connect alias (D-006)
  ecr_registry = "${var.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  image_prefix = "${local.ecr_registry}/${local.name}"
}

module "network" {
  source = "../../modules/network"

  name     = local.name
  region   = var.region
  azs      = var.azs
  vpc_cidr = var.vpc_cidr
}

module "kms" {
  source = "../../modules/kms"

  name       = local.name
  account_id = var.account_id
}

module "s3_documents" {
  source = "../../modules/s3_documents"

  bucket_name = "${local.name}-documents-${var.account_id}"
  kms_key_arn = module.kms.key_arns["documents"]
  web_origin  = local.web_origin
}

module "ecr" {
  source = "../../modules/ecr"

  name        = local.name
  kms_key_arn = module.kms.key_arns["ecr"]
}

module "rds" {
  source = "../../modules/rds"

  name                = local.name
  vpc_id              = module.network.vpc_id
  db_subnet_ids       = module.network.db_subnet_ids
  rds_kms_key_arn     = module.kms.key_arns["rds"]
  secrets_kms_key_arn = module.kms.key_arns["secrets"]
  instance_class      = var.db_instance_class
}

module "secrets" {
  source = "../../modules/secrets"

  name        = "kyro/staging"
  kms_key_arn = module.kms.key_arns["secrets"]
}

module "ecs_cluster" {
  source = "../../modules/ecs_cluster"

  name = local.name
}

module "alb" {
  source = "../../modules/alb"

  name                      = local.name
  vpc_id                    = module.network.vpc_id
  vpc_cidr                  = module.network.vpc_cidr
  public_subnet_ids         = module.network.public_subnet_ids
  cloudfront_prefix_list_id = var.cloudfront_prefix_list_id
  acm_certificate_arn       = var.alb_certificate_arn
  web_host                  = var.web_host
  admin_host                = var.admin_host
}

# ----- api task role: least privilege (documents bucket, its key, its own secrets) -----
data "aws_iam_policy_document" "api_task" {
  statement {
    sid       = "DocumentsObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${module.s3_documents.bucket_arn}/*"]
  }

  statement {
    sid       = "DocumentsBucket"
    actions   = ["s3:ListBucket", "s3:GetBucketLocation"]
    resources = [module.s3_documents.bucket_arn]
  }

  statement {
    sid       = "DocumentsKey"
    actions   = ["kms:GenerateDataKey", "kms:Decrypt"]
    resources = [module.kms.key_arns["documents"]]
  }

  statement {
    sid     = "OwnSecrets"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      module.rds.master_secret_arn,
      module.secrets.secret_arns["mfa-encryption-key"],
      module.secrets.secret_arns["mail-smtp"],
    ]
  }

  statement {
    sid       = "OwnSecretsKey"
    actions   = ["kms:Decrypt"]
    resources = [module.kms.key_arns["secrets"]]
  }
}

module "api" {
  source = "../../modules/ecs_service"

  name                = "api"
  cluster_name        = local.name
  cluster_arn         = module.ecs_cluster.cluster_arn
  namespace_arn       = module.ecs_cluster.namespace_arn
  region              = var.region
  vpc_id              = module.network.vpc_id
  vpc_cidr            = module.network.vpc_cidr
  subnet_ids          = module.network.private_subnet_ids
  image               = "${local.image_prefix}/api:${var.image_tag}"
  ecr_repository_arn  = module.ecr.repository_arns["api"]
  container_port      = 8080
  cpu                 = 1024
  memory              = 2048
  desired_count       = var.api_desired_count # pinned to 1 by variable validation
  target_group_arn    = module.alb.target_group_arns["api"]
  has_target_group    = true
  secrets_kms_key_arn = module.kms.key_arns["secrets"]
  log_retention_days  = var.log_retention_days

  service_connect_alias = "api"

  ingress_security_group_ids = {
    alb   = module.alb.security_group_id
    web   = module.web.security_group_id
    admin = module.admin.security_group_id
  }

  environment = {
    SPRING_PROFILES_ACTIVE        = "staging"
    KYRO_FORWARD_HEADERS_STRATEGY = "native"
    KYRO_ADMIN_MFA_ENFORCED       = "true"
    KYRO_SEED_ENABLED             = "false"
    KYRO_WEB_BASE_URL             = local.web_origin
    KYRO_DB_URL                   = "jdbc:postgresql://${module.rds.address}:${module.rds.port}/${module.rds.db_name}?sslmode=require"
    KYRO_S3_REGION                = var.region
    KYRO_S3_DOCUMENTS_BUCKET      = module.s3_documents.bucket_name
    KYRO_MAIL_HOST                = var.mail_host
    KYRO_MAIL_PORT                = "587"
    KYRO_MAIL_FROM                = var.mail_from
  }

  # No static AWS keys: KYRO_S3_ACCESS_KEY / KYRO_S3_SECRET_KEY / endpoints stay unset (task IAM role).
  secrets = {
    KYRO_DB_USER            = "${module.rds.master_secret_arn}:username::"
    KYRO_DB_PASSWORD        = "${module.rds.master_secret_arn}:password::"
    KYRO_MFA_ENCRYPTION_KEY = module.secrets.secret_arns["mfa-encryption-key"]
    KYRO_MAIL_USERNAME      = "${module.secrets.secret_arns["mail-smtp"]}:username::"
    KYRO_MAIL_PASSWORD      = "${module.secrets.secret_arns["mail-smtp"]}:password::"
  }

  secret_arns = [
    module.rds.master_secret_arn,
    module.secrets.secret_arns["mfa-encryption-key"],
    module.secrets.secret_arns["mail-smtp"],
  ]

  task_role_policy_json   = data.aws_iam_policy_document.api_task.json
  attach_task_role_policy = true
}

# Web and admin images are built with KYRO_API_ORIGIN=http://api:8080 (the Service Connect name).
module "web" {
  source = "../../modules/ecs_service"

  name                = "web"
  cluster_name        = local.name
  cluster_arn         = module.ecs_cluster.cluster_arn
  namespace_arn       = module.ecs_cluster.namespace_arn
  region              = var.region
  vpc_id              = module.network.vpc_id
  vpc_cidr            = module.network.vpc_cidr
  subnet_ids          = module.network.private_subnet_ids
  image               = "${local.image_prefix}/web:${var.image_tag}"
  ecr_repository_arn  = module.ecr.repository_arns["web"]
  container_port      = 3000
  desired_count       = var.web_desired_count
  target_group_arn    = module.alb.target_group_arns["web"]
  has_target_group    = true
  secrets_kms_key_arn = module.kms.key_arns["secrets"]
  log_retention_days  = var.log_retention_days

  ingress_security_group_ids = { alb = module.alb.security_group_id }

  environment = {
    NODE_ENV        = "production"
    KYRO_API_ORIGIN = local.api_origin
  }
}

module "admin" {
  source = "../../modules/ecs_service"

  name                = "admin"
  cluster_name        = local.name
  cluster_arn         = module.ecs_cluster.cluster_arn
  namespace_arn       = module.ecs_cluster.namespace_arn
  region              = var.region
  vpc_id              = module.network.vpc_id
  vpc_cidr            = module.network.vpc_cidr
  subnet_ids          = module.network.private_subnet_ids
  image               = "${local.image_prefix}/admin:${var.image_tag}"
  ecr_repository_arn  = module.ecr.repository_arns["admin"]
  container_port      = 3001
  desired_count       = var.admin_desired_count
  target_group_arn    = module.alb.target_group_arns["admin"]
  has_target_group    = true
  secrets_kms_key_arn = module.kms.key_arns["secrets"]
  log_retention_days  = var.log_retention_days

  ingress_security_group_ids = { alb = module.alb.security_group_id }

  environment = {
    NODE_ENV        = "production"
    KYRO_API_ORIGIN = local.api_origin
  }
}

# Only the api tasks may reach Postgres.
resource "aws_vpc_security_group_ingress_rule" "db_from_api" {
  security_group_id            = module.rds.security_group_id
  description                  = "Postgres from api tasks"
  referenced_security_group_id = module.api.security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

module "edge" {
  source = "../../modules/edge"

  providers = {
    aws = aws.us_east_1
  }

  name                       = local.name
  origin_domain_name         = module.alb.dns_name
  web_host                   = var.web_host
  admin_host                 = var.admin_host
  cloudfront_certificate_arn = var.cloudfront_certificate_arn
  admin_allowed_cidrs        = var.admin_allowed_cidrs
}

module "github_oidc" {
  source = "../../modules/github_oidc"

  name                     = local.name
  github_repo              = var.github_repo
  create_oidc_provider     = var.create_github_oidc_provider
  oidc_provider_arn        = var.github_oidc_provider_arn
  state_bucket_name        = var.state_bucket_name
  documents_bucket_arn     = module.s3_documents.bucket_arn
  ecr_repository_arns      = values(module.ecr.repository_arns)
  ecs_cluster_arn          = module.ecs_cluster.cluster_arn
  ecs_service_arns_pattern = "arn:aws:ecs:${var.region}:${var.account_id}:service/${local.name}/*"

  task_role_arns = [
    module.api.task_role_arn,
    module.api.execution_role_arn,
    module.web.task_role_arn,
    module.web.execution_role_arn,
    module.admin.task_role_arn,
    module.admin.execution_role_arn,
  ]
}

module "observability" {
  source = "../../modules/observability"

  name                    = local.name
  alarm_sns_topic_arn     = var.alarm_sns_topic_arn
  alb_arn_suffix          = module.alb.arn_suffix
  ecs_cluster_name        = module.ecs_cluster.cluster_name
  ecs_service_names       = toset(["api", "web", "admin"])
  rds_instance_identifier = module.rds.identifier
}

module "ses" {
  source = "../../modules/ses"

  enable_ses = var.enable_ses
  domain     = var.ses_domain
}

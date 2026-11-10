# Terraform staging runbook

Scope: the AWS staging foundation in `infrastructure/terraform` (INF-2). In Phase 5 this code is **validated only**. Nothing has been applied, no workflow applies it, and no AWS credentials exist in CI. Applying needs a human and written approval (section 6).

## 1. What it creates

`envs/staging` wires these modules (`modules/`):

| Module | Resources |
|---|---|
| network | VPC, 2 AZs, public (ALB, NAT), private-app and isolated-db subnets, **one** NAT gateway, S3 gateway endpoint, interface endpoints (ECR api and dkr, Secrets Manager, Logs), flow logs |
| kms | Customer-managed keys (rotation on): documents, rds, secrets, ecr |
| s3_documents | Private bucket (all public access blocked), versioning, SSE-KMS, deny non-TLS and non-KMS puts, CORS PUT from the web origin only, noncurrent expiry, GuardDuty malware protection plan |
| ecr | api, web, admin repositories: immutable tags, scan on push, KMS, lifecycle policy |
| rds | Postgres 17, private isolated subnets, encrypted, RDS-managed master password, 7-day backups with PITR, deletion protection, `rds.force_ssl=1` |
| secrets | `kyro/staging/mfa-encryption-key`, `kyro/staging/mail-smtp` (containers only, no values) |
| ecs_cluster, ecs_service | Fargate cluster, Service Connect namespace, services api, web, admin, least-privilege task and execution roles, log group per service |
| alb | Internet-facing ALB reachable only from the CloudFront origin-facing prefix list, HTTPS listener, HTTP to HTTPS redirect, host and `/api/*` routing |
| edge | CloudFront in front of the ALB with a WAFv2 web ACL (Common, KnownBadInputs, SQLi managed groups, rate-based rule, admin hostname IP allowlist), created in us-east-1 |
| github_oidc | OIDC provider and two roles: `kyro-staging-tf-plan` (read-only, `pull_request` of the repo) and `kyro-staging-deploy` (`environment:staging` only). **Nothing assumes either role in Phase 5.** |
| observability | Alarms: ALB target 5xx, ECS CPU and memory per service, RDS free storage and CPU. SNS topic is a variable |
| ses | Domain identity and DKIM outputs behind `enable_ses = false` |

Routing: `web_host` goes to web, `admin_host` goes to admin, `/api/*` on either host goes to the api. Web and admin call the api as `http://api:8080` through Service Connect (D-006).

## 2. Validate locally (no AWS access, Docker only)

```bash
docker run --rm -v "$PWD/infrastructure/terraform:/tf" -w /tf hashicorp/terraform:1.13 fmt -check -recursive
docker run --rm -v "$PWD/infrastructure/terraform:/tf" -w /tf/envs/staging hashicorp/terraform:1.13 init -backend=false
docker run --rm -v "$PWD/infrastructure/terraform:/tf" -w /tf/envs/staging hashicorp/terraform:1.13 validate
docker run --rm -v "$PWD:/repo" -w /repo aquasec/trivy config --severity HIGH,CRITICAL --exit-code 1 infrastructure/terraform
docker run --rm -v "$PWD/infrastructure/terraform:/tf" -w /tf ghcr.io/terraform-linters/tflint --recursive
```

Offline plan (mock credentials, no AWS calls):

```bash
cd infrastructure/terraform/envs/staging
terraform init -backend=false
AWS_ACCESS_KEY_ID=mock AWS_SECRET_ACCESS_KEY=mock \
  terraform plan -refresh=false -lock=false -input=false \
  -var offline_plan=true -var-file=staging.tfvars.example
```

`offline_plan` only sets the provider `skip_*` flags. Never set it for a real plan or apply. The same checks run in `.github/workflows/terraform.yml`.

## 3. Bootstrap the state bucket (manual, once)

Terraform cannot create its own state bucket. A human with admin rights in the staging account does this once, outside Terraform:

1. Create a private bucket, for example `kyro-tfstate-<account-id>` in the staging region.
2. Enable **Block Public Access** (all four), **versioning**, and default **SSE-KMS** (or SSE-S3).
3. Add a bucket policy denying non-TLS access. Restrict access to the platform admins and the plan role from `github_oidc`.
4. Copy `envs/staging/backend.hcl.example` to `backend.hcl` (git-ignored), fill in the bucket, and run `terraform init -backend-config=backend.hcl`. Locking is S3-native (`use_lockfile = true`, Terraform 1.10 or newer). No DynamoDB table is needed.
5. Put the same bucket name into `state_bucket_name` in your `staging.tfvars`.

## 4. Fill in `staging.tfvars` (never commit it)

Copy `staging.tfvars.example` to `staging.tfvars` (git-ignored) and replace every placeholder. The example uses the fictional account `000000000000` and `example.com` names.

- `account_id`, `region`, `azs`
- `web_host`, `admin_host`
- `alb_certificate_arn` (regional ACM) and `cloudfront_certificate_arn` (us-east-1 ACM), both covering web and admin hostnames and DNS-validated
- `cloudfront_prefix_list_id`: `aws ec2 describe-managed-prefix-lists --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing`
- `admin_allowed_cidrs`: office or VPN IPv4 ranges. Empty means the admin hostname is blocked for everyone
- `github_repo` (default scoping `arx-studios/arx-kyro`), `create_github_oidc_provider` (false if the account already has the GitHub OIDC provider)
- `image_tag`: an immutable tag that already exists in ECR. For a first apply, push a bootstrap image set before the services can become healthy

## 5. Fill in the secrets (out of band)

Terraform creates the secret containers without values. Set them with the AWS CLI or console so they never touch git or state:

```bash
aws secretsmanager put-secret-value --secret-id kyro/staging/mfa-encryption-key \
  --secret-string "$(openssl rand -base64 32)"
aws secretsmanager put-secret-value --secret-id kyro/staging/mail-smtp \
  --secret-string '{"username":"<smtp user>","password":"<smtp password>"}'
```

The database master password is generated and rotated by RDS in its own Secrets Manager secret. The api reads `username` and `password` from it through ECS secret injection.

The api task gets `KYRO_MFA_ENCRYPTION_KEY` (`kyro.mfa.encryption-key`: base64 of 32 bytes, required outside local and test, so the api will not start until the secret has a value).

## 6. Plan and apply (human, written approval only)

1. Plan: a human runs `terraform plan -var-file=staging.tfvars -out=tfplan` using the read-only plan role (or their own admin SSO). Review the plan in the PR.
2. Apply only with **written approval** from the owner, using a human admin session. No workflow applies Terraform. The deploy OIDC role can push images and roll ECS services only. It cannot run Terraform.
3. After apply: create the DNS records (hostnames to the `cloudfront_domain_name` output), set the secrets (section 5), push images, then check `/actuator/health` through the ALB target group and the app through CloudFront.
4. First-time order: apply with the KMS, ECR and secrets modules first (`-target`) if you need to push images before the services exist.

## 7. Cost estimate (staging, rough, on-demand, one region)

| Item | Approx. monthly |
|---|---|
| NAT gateway (1) plus data | 35 USD |
| Interface endpoints (4 x 2 AZ) | 55 USD |
| ALB | 20 USD |
| Fargate: api 1 vCPU 2 GB, web and admin 0.5 vCPU 1 GB | 70 USD |
| RDS db.t4g.micro, 20 GB gp3, 7-day backups | 20 USD |
| CloudFront, WAF (ACL plus 5 rules), KMS keys, Secrets, logs | 25 USD |
| Total | about 220 to 250 USD |

To save money before launch, drop the interface endpoints (NAT then carries ECR and Secrets traffic) and run web and admin at the minimum task size. Estimates only. Check the AWS pricing calculator before approving.

## 8. Known limits and open risks

- **Single API task.** `api_desired_count` is validated to equal 1. Sessions and the rate limiter are in memory, so a second task would break logins and let rate limits be bypassed. Lift this only after Spring Session JDBC and shared rate limits land (Phase 6).
- **Single NAT gateway and single-AZ RDS.** Staging cost choice. An AZ outage takes staging down. Production needs NAT per AZ and Multi-AZ RDS.
- **S3 malware scanning gap.** GuardDuty Malware Protection for S3 scans new objects and tags them. The application does **not** yet check the tag (`scan_status` must be clean before a view URL is issued). Until that Phase 6 change lands, do not accept real uploaded documents on staging.
- **Forwarded client IP behind CloudFront.** The api runs with `server.forward-headers-strategy=native` and trusts `X-Forwarded-For` only from the ALB (private 10.x addresses). The chain is client, CloudFront edge, ALB. Tomcat strips the ALB and then stops at the CloudFront edge address (a public IP that is not trusted), so the rate limiter keys on the CloudFront edge IP, not the end user. That is better than one shared bucket but coarse. Fix before real traffic: trust the CloudFront ranges in `server.tomcat.remoteip.trusted-proxies`, or read the `CloudFront-Viewer-Address` header (needs an origin request policy change and an api change).
- **ALB origin header.** Only the CloudFront prefix list reaches the ALB. Any CloudFront distribution (including another customer's) uses the same prefix list. Add a secret origin header check before production.
- **Access logging.** S3, CloudFront and ALB access logs are not enabled in Phase 5 (accepted, see trivy ignores in the code).
- **No payments, no real money flows.** This environment is for fictional data only.

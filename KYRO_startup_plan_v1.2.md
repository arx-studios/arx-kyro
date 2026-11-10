# KYRO --- Startup Plan

**Brand:** KYRO\
**Product concept:** A premium, trustworthy, swipe-first marketplace
connecting people with verified education-related expenses to donors
willing to help pay them.\
**Document version:** 1.1\
**Planning status:** Product and engineering blueprint; not a statement
that the platform is built, legally approved, or ready to process live
payments.

------------------------------------------------------------------------

## 1. Executive Summary

**Brand note:** KYRO is the selected working name. It is a creative
selection only; trademark, company-name, domain, and app-store
availability have not yet been verified. Do not publish or incorporate
under the name until appropriate clearance is complete.

KYRO is a proposed platform that makes it easier for people with
legitimate financial needs to connect with people who want to help. The
initial product focuses on **education and tuition bills**.

Recipients submit education-expense requests and supporting documents.
The platform verifies requests before publishing privacy-safe cards in a
donor discovery feed. Donors can pass, save, express interest, pledge an
amount, and---once the payment flow is legally and operationally
approved---make a contribution.

The product borrows the intuitive discovery mechanic of swipe-based
apps, but its core value is trust: verified requests, clear funding
status, transparent destinations for money, privacy protections, and
reliable financial records.

### Product decisions

  ---------------------------------------------------------------------
  Area                               Decision
  ---------------------------------- ----------------------------------
  Brand                              Keep the working title for now; do
                                     not finalize identity yet

  Positioning                        Premium, trustworthy, transparent

  Vision                             Global product, launch in one
                                     market first

  Initial market recommendation      India, subject to legal and
                                     payment-provider validation

  Initial category                   Education and tuition bills

  Funding model                      Hybrid: direct-to-issuer
                                     settlement where supported and
                                     compliant, plus verified
                                     group-funded campaigns

  Initial product                    Swipe-first mobile web application
                                     / PWA

  Engineering capacity               Funded startup with
                                     production-grade resources

  Architecture                       Modular monolith, independently
                                     scalable workers

  Backend                            Java 21+ + Spring Boot (proposed
                                     baseline)

  Frontend                           Next.js + TypeScript (proposed
                                     baseline)

  Database                           PostgreSQL (selected)

  Cloud                              AWS (selected)

  Deployment                         ECS Fargate

  Database migrations                Flyway

  API documentation                  OpenAPI

  Project management                 GitHub Projects

  Architecture decision              Modular monolith + independent SQS
                                     workers

  Cache                              Redis optional; PostgreSQL remains
                                     source of truth
  ---------------------------------------------------------------------

### Mission

Make meaningful financial help easier to discover, safer to deliver, and
easier to trust.

### Product promise

**Verified needs. Meaningful impact.**

The platform should not make recipients perform, compete, or expose
unnecessary personal hardship to attract funding. The swipe interface is
a discovery tool; dignity, consent, and financial integrity remain
central.

------------------------------------------------------------------------

## 2. Problem and Opportunity

People may have genuine education expenses---such as tuition,
examination fees, or required academic costs---but lack the funds to pay
them on time. Potential donors may want to help but cannot easily
establish whether a request is authentic, whether it is still
outstanding, or where the money will go.

The platform aims to solve both sides:

### Recipient problems

-   Difficulty reaching potential supporters.
-   Repeatedly explaining the same financial situation.
-   Donor uncertainty and low trust.
-   Lack of transparent tracking for partial funding.
-   Privacy risks when sharing bills publicly.

### Donor problems

-   Difficulty discovering legitimate needs.
-   Uncertainty about bill authenticity and outstanding balances.
-   Unclear use of funds and payment destination.
-   Lack of reliable receipts and outcome reporting.
-   Friction in supporting a request with a small or partial
    contribution.

### Platform opportunity

Create a trusted workflow that handles request intake, verification,
discovery, pledges, payments, reconciliation, refunds, and outcome
reporting.

The north-star metric is **verified education expenses successfully
settled**, not swipes, downloads, or pledged amounts.

------------------------------------------------------------------------

## 3. Target Users and Roles

  ---------------------------------------------------------------------
  Role                               Responsibilities
  ---------------------------------- ----------------------------------
  Recipient                          Creates education funding
                                     requests, provides evidence,
                                     tracks review and funding

  Donor                              Discovers requests, expresses
                                     interest, pledges, pays, and
                                     tracks outcomes

  Verifier                           Reviews documents, institution
                                     details, duplicate indicators, and
                                     request eligibility

  Administrator                      Manages users, campaigns,
                                     complaints, fraud cases, and
                                     operations

  Support agent                      Handles user questions and
                                     permitted operational
                                     investigations

  Institution / organization         May verify or receive payment for
                                     an education expense where an
                                     approved integration exists
  ---------------------------------------------------------------------

A user may eventually act as both donor and recipient, subject to
appropriate verification and abuse controls.

------------------------------------------------------------------------

## 4. Initial Market and Launch Strategy

### Recommended first market

Provisionally launch in **India**, with education and tuition as the
first expense category. This is a strategic starting recommendation, not
a conclusion that India is automatically the easiest jurisdiction.

Reasons to test this market first:

-   A narrow education use case is easier to validate than many
    unrelated bill categories.
-   Tuition statements and institution references may provide useful
    verification evidence.
-   A focused cohort makes moderation, support, and fraud review
    manageable.
-   The product can be designed for international expansion without
    enabling cross-border payments on day one.

### Market-validation work

Before accepting money:

1.  Interview prospective recipients, donors, universities, and
    education organizations.
2.  Confirm what documents institutions issue and how outstanding fees
    can be verified.
3.  Identify eligible recipient and donor categories.
4.  Obtain qualified legal advice on the fundraising structure.
5.  Obtain payment-provider confirmation that the intended payment flow
    is supported.
6.  Define refunds, complaints, disputes, payout timing, and financial
    recordkeeping.
7.  Validate privacy and data-retention requirements.

### Pilot proposal

Initial controlled pilot targets (planning assumptions, not market
benchmarks):

-   20--50 verified education requests.
-   50--100 invited potential donors.
-   A small number of trusted institutional partners.
-   Manual verification for every public request.
-   A documented support, complaint, and fraud escalation process.

Do not expand to medical, housing, or general emergency bills until
category-specific verification and abuse controls have been designed and
tested.

------------------------------------------------------------------------

## 5. Product Experience and Core Journeys

### Journey A: Recipient submits a request

1.  Register and verify contact details.
2.  Create a recipient profile.
3.  Select education expense type and currency.
4.  Enter institution, fee reference, amount, due date, and explanation.
5.  Upload supporting documents to private storage.
6.  Submit the request for review.
7.  Respond to any correction request.
8.  Receive an approval or rejection decision.
9.  If approved, the request appears in the donor discovery feed.
10. Track pledges, confirmed payments, refunds, and settlement.

### Journey B: Donor discovers and funds a request

1.  Register and complete required verification.
2.  Choose preferences such as cause, budget, and currency.
3.  Browse approved request cards.
4.  Swipe to pass or express interest; save requests for later.
5.  Open request details and review verification information.
6.  Choose an amount.
7.  Review the destination, fees, payment terms, and refund conditions.
8.  Explicitly confirm checkout.
9.  Pay through an approved payment provider.
10. Receive a receipt and later view a privacy-safe outcome.

### Journey C: Verification and operations

1.  A submitted request enters a review queue.
2.  A verifier checks the bill, institution, amount, recipient evidence,
    and duplicate indicators.
3.  The verifier approves, rejects, or requests corrections.
4.  The decision and rationale are recorded in an audit trail.
5.  Payment events are authenticated and reconciled.
6.  Discrepancies, refunds, disputes, and reports are assigned and
    resolved.
7.  Recipients and donors see appropriate status updates.

------------------------------------------------------------------------

## 6. Product Requirements

### P0 --- Required for private beta

-   Registration and authentication.
-   Recipient and donor profiles.
-   Education request creation and editing while draft.
-   Private supporting-document upload.
-   Manual verification queue.
-   Approval, rejection, and correction workflow.
-   Swipe discovery feed containing approved requests only.
-   Pass, interested, and save actions.
-   Request details and privacy-safe funding progress.
-   Pledges clearly separated from confirmed payments.
-   Admin tools, user reporting, and audit events.
-   Object-level authorization and security controls.

### P1 --- Required for a real-money pilot

-   Approved payment-provider integration.
-   Provider-hosted checkout.
-   Payment webhook verification and idempotency.
-   Double-entry ledger and reconciliation.
-   Funding reservations to prevent concurrent overfunding.
-   Refund workflow.
-   Dispute and complaint management.
-   Direct-to-institution settlement where supported.
-   Group-funded campaign workflow.
-   Payout and settlement records.
-   Operational monitoring and incident response.

### P2 --- Enhancements

-   OCR to extract bill details for human review.
-   Institution verification integrations.
-   Donor impact dashboard.
-   Organization accounts and employer giving.
-   Multilingual experience.
-   Better discovery filters and recommendations.
-   Automated fraud indicators with human review.

### P3 --- International expansion

-   Additional currencies and country-specific configuration.
-   Local payment-provider integrations.
-   Jurisdiction-specific compliance and receipts.
-   Cross-border funding only where expressly permitted.
-   Localized support and institution integrations.

### Explicit MVP exclusions

-   Native iOS and Android apps.
-   Cryptocurrency or token donations.
-   An internally operated wallet or transferable stored balance.
-   Anonymous public requests.
-   Automatic AI-only verification decisions.
-   Global cross-border disbursements.
-   Public leaderboards ranking recipients by hardship.
-   Unmoderated chat between strangers.

------------------------------------------------------------------------

## 7. Funding Model and Money Flows

The system must distinguish three concepts:

1.  **Interest:** a donor wants to consider a request.
2.  **Pledge:** a donor states an intended contribution; funds are not
    yet confirmed.
3.  **Payment:** a provider confirms a transaction, which must be
    reconciled and settled.

A swipe or pledge must never be counted as money raised.

### Mode A --- Direct bill settlement

Where supported and compliant, a donor contribution is routed to the
education institution or approved bill issuer. The platform records the
payment against the verified expense.

### Mode B --- Group-funded campaign

Multiple donors contribute toward one approved education target. The
campaign tracks confirmed contributions, remaining eligible need,
refunds, and settlement.

A group-funded campaign does not imply that the startup may hold pooled
customer funds in its own bank account. The legal and provider-approved
fund flow must be established first.

### Funding controls

-   Do not accept contributions beyond the verified eligible outstanding
    amount.
-   Use transactional reservations to handle concurrent donors.
-   Release expired reservations safely.
-   Reconcile provider events and settlement reports.
-   Support partial refunds and reversals.
-   Preserve an immutable financial audit trail.
-   Do not treat a frontend redirect as payment confirmation.

------------------------------------------------------------------------

## 8. Revenue Model

Recommended revenue streams:

  ---------------------------------------------------------------------
  Revenue stream                     Strategy
  ---------------------------------- ----------------------------------
  Voluntary donor tips               Optional, clearly disclosed, never
                                     preselected

  Corporate-sponsored campaigns      Primary early monetization
                                     opportunity

  Employer giving programs           Later matching contributions and
                                     employee campaigns

  Premium impact reporting           Later B2B or high-volume donor
                                     tools

  Service fee                        Only if lawful, provider-approved,
                                     transparent, and commercially
                                     appropriate

  Institutional verification tools   Potential B2B subscription
  ---------------------------------------------------------------------

Keep the core recipient experience free or low-friction. Do not make
access to assistance dependent on paying the platform.

### Illustrative unit economics

Example only: ₹10,00,000 of confirmed funding facilitated in a month.

  Metric                                                         Illustrative amount
  ------------------------------------------------------------ ---------------------
  Confirmed funding volume                                                ₹10,00,000
  Voluntary tips / sponsorship revenue allocated to platform                 ₹30,000
  Processing and variable operational costs                                  ₹18,000
  Contribution before fixed overhead                                         ₹12,000

These are hypothetical assumptions, not forecasts or payment-provider
fee quotes. Facilitated funding volume is not platform revenue. Actual
accounting depends on the legal structure and contracts.

------------------------------------------------------------------------

## 9. Business Metrics

Track:

-   Verified request rate.
-   Time to first confirmed contribution.
-   Funding completion rate.
-   Donor repeat rate.
-   Fraud loss rate.
-   Refund and dispute rate.
-   Contribution margin.
-   Recipient outcome rate.
-   Verified expenses successfully settled.

Do not optimize for swipe volume at the expense of recipient dignity or
donor understanding.

------------------------------------------------------------------------

## 10. Technical Architecture

### Architecture decision

Use a **modular monolith** for the core application, with independently
scalable background workers. Avoid premature microservices.

### High-level architecture

``` text
Next.js PWA
  ├── Donor experience
  ├── Recipient portal
  └── Public campaign pages
          |
          v
Spring Boot REST API
  ├── Identity and access
  ├── Recipient profiles
  ├── Education requests
  ├── Verification
  ├── Discovery and matching
  ├── Pledges and funding
  ├── Payment orchestration
  ├── Double-entry ledger
  ├── Reconciliation
  ├── Notifications
  └── Administration
          |
          +---- PostgreSQL
          +---- Private object storage
          +---- Approved payment provider
          +---- Background queue and workers
          +---- Monitoring and audit systems
```

### Technology stack

  ---------------------------------------------------------------------
  Layer                              Technology
  ---------------------------------- ----------------------------------
  Frontend                           Next.js, TypeScript, Tailwind CSS

  Backend                            Java 21+ and Spring Boot

  API                                REST and OpenAPI

  Database                           PostgreSQL (selected)

  Migrations                         Flyway

  Storage                            Amazon S3 private object storage

  Background jobs                    Amazon SQS and independently
                                     scalable ECS Fargate workers

  Authentication                     OIDC-compatible identity
                                     provider + Spring Security; secure
                                     server-side sessions

  Payments                           Approved provider-hosted checkout

  Cloud                              AWS (selected)

  Compute                            ECS Fargate

  Infrastructure as Code             Terraform

  Observability                      CloudWatch + OpenTelemetry; X-Ray
                                     or compatible trace backend

  Project management                 GitHub Projects

  Architecture decision              Modular monolith + independent SQS
                                     workers

  Cache                              Redis optional; PostgreSQL remains
                                     source of truth

  API client                         Generated TypeScript client

  Cache / rate limiting              ElastiCache for Redis (add when
                                     justified)

  Edge / web protection              CloudFront + AWS WAF

  Secrets                            AWS Secrets Manager + KMS

  Container registry                 Amazon ECR

  DNS / certificates                 Route 53 + AWS Certificate Manager

  CI/CD                              GitHub Actions + OIDC to AWS
  ---------------------------------------------------------------------

Use supported, patched software versions at implementation time.

### Final architecture decisions

The selected architecture is a **modular monolith with independently
deployable worker processes**. KYRO will not begin as a microservices
fleet. Domain boundaries, module-owned persistence, explicit application
interfaces, and domain events will make later extraction possible
without paying the distributed-systems tax prematurely.

#### Runtime topology

-   **Web:** Next.js application deployed as a container on ECS Fargate
    behind CloudFront and an Application Load Balancer. Use a separate
    deployment for the restricted admin application.
-   **API:** Spring Boot REST API deployed on ECS Fargate in private
    application subnets, reachable through the ALB.
-   **Workers:** Separate Spring Boot worker deployment(s) consuming SQS
    queues for document processing, notifications, scheduled expiry,
    provider webhook follow-up, and reconciliation.
-   **Database:** Amazon RDS for PostgreSQL in private database subnets,
    Multi-AZ for production, automated backups and point-in-time
    recovery.
-   **Documents:** Private S3 buckets with encryption, versioning where
    appropriate, malware scanning/quarantine, short-lived presigned
    URLs, and no public object access.
-   **Edge/security:** Route 53, ACM, CloudFront, AWS WAF, ALB, Secrets
    Manager, KMS, IAM task roles, and ECR.
-   **Operations:** CloudWatch logs/metrics/alarms, OpenTelemetry
    traces, centralized audit events, and documented incident runbooks.

#### Backend implementation standards

-   Java 21+ LTS and a supported Spring Boot release at implementation.
-   Spring MVC for the initial HTTP API; Spring Security for
    authorization.
-   Spring Data JPA for ordinary transactional persistence; use explicit
    SQL/JdbcTemplate for ledger posting and concurrency-critical paths.
-   Flyway for all schema changes; migrations are reviewed and run once
    through a controlled deployment step.
-   REST APIs documented with OpenAPI; generate a typed TypeScript
    client.
-   Jakarta Bean Validation at API boundaries; consistent error
    envelopes with correlation IDs.
-   Package by business domain, with `api`, `application`, `domain`, and
    `infrastructure` boundaries. Do not allow one module to access
    another module's repositories directly.
-   Use transactional outbox records for reliable publication of domain
    events to SQS. Consumers must be idempotent.
-   Use optimistic locking for ordinary edits and explicit row-level
    locking / atomic conditional updates for funding reservations.

#### Frontend implementation standards

-   Next.js App Router, TypeScript, Tailwind CSS, and a shared design
    system.
-   TanStack Query for server state; React Hook Form + Zod for forms.
-   Use accessible, keyboard-operable swipe alternatives (buttons and
    save/pass controls); gestures must never be the only interaction.
-   Treat the server as authoritative for feed eligibility, funding
    availability, permissions, and payment state.
-   Keep payment card data on the approved provider's hosted checkout;
    KYRO must not store raw card details.
-   Use a separately deployed admin frontend with stronger access
    controls, MFA, and auditable privileged actions.

#### Data and financial integrity

-   PostgreSQL is the system of record. Redis is only a cache /
    ephemeral coordination layer and must never be authoritative for
    balances, pledges, payments, or ledger entries.
-   Store money in integer minor units with ISO 4217 currency codes.
-   Enforce idempotency for pledge creation, payment initiation,
    provider webhooks, refunds, and ledger posting.
-   Reserve campaign capacity atomically before checkout; expire
    abandoned reservations safely.
-   Post financial movements through a dedicated ledger service. Posted
    entries are immutable and balanced per currency; corrections use
    compensating entries.
-   A provider redirect is not proof of payment. Verify signed webhooks
    and reconcile provider reports against internal payment and ledger
    records.
-   Never hold customer funds in an unapproved KYRO wallet or pool money
    without the required legal structure and provider approval.

#### AWS environment layout

Use separate AWS accounts (or equivalent strong isolation) for
production and non-production. Keep RDS and worker services private;
restrict ingress, use least-privilege IAM task roles, encrypt data in
transit and at rest, and protect production secrets. Terraform should
manage repeatable infrastructure. Deploy immutable container images
through ECR with GitHub Actions using short-lived OIDC credentials.
Production changes require review, health checks, rollback capability,
and database migration compatibility.

#### Initial deployment units

1.  `kyro-web` --- Next.js donor/recipient PWA.
2.  `kyro-admin` --- restricted operations interface.
3.  `kyro-api` --- Spring Boot modular monolith.
4.  `kyro-worker` --- background consumers; scale by queue depth.
5.  `kyro-db` --- managed PostgreSQL, not a self-managed container.
6.  `kyro-cache` --- optional ElastiCache Redis, introduced only when
    measured needs justify it.

#### Decisions deliberately deferred

-   Microservice extraction and Kubernetes.
-   Redis adoption before measured cache / rate-limit needs.
-   Native iOS/Android clients.
-   GraphQL, event streaming platforms, and multi-region active-active.
-   A specific identity vendor and payment provider until KYRO's
    jurisdiction, fund flow, and provider eligibility are confirmed.

### Backend modules

1.  Identity and access management.
2.  Recipient and organization profiles.
3.  Education requests and campaigns.
4.  Document verification.
5.  Discovery, preferences, and swipe actions.
6.  Pledges and payment orchestration.
7.  Double-entry ledger.
8.  Refunds, disputes, and reconciliation.
9.  Notifications.
10. Administration, fraud monitoring, and audit trails.

Each module owns its domain model and application services. Cross-module
access should use explicit interfaces or application services rather
than direct repository access.

------------------------------------------------------------------------

## 11. Repository Structure

``` text
kyro/
├── apps/
│   ├── web/                 # Next.js donor and recipient PWA
│   ├── admin/               # Restricted operations dashboard
│   ├── api/                 # Spring Boot backend
│   └── worker/              # Background processing
├── packages/
│   ├── design-system/
│   ├── api-client/
│   ├── schemas/
│   └── config/
├── infrastructure/
│   ├── terraform/
│   ├── docker/
│   ├── monitoring/
│   └── security/
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── product/
│   ├── compliance/
│   └── runbooks/
├── .github/
│   └── workflows/
├── pnpm-workspace.yaml
├── package.json
└── README.md
```

### Spring Boot structure

``` text
apps/api/src/main/java/com/kyro/platform/
├── PlatformApplication.java
├── common/
│   ├── config/
│   ├── errors/
│   ├── security/
│   ├── observability/
│   └── validation/
├── identity/
│   ├── api/
│   ├── domain/
│   └── infrastructure/
├── education/
│   ├── api/
│   ├── domain/
│   └── infrastructure/
├── verification/
├── discovery/
├── funding/
├── payments/
├── ledger/
├── reconciliation/
├── notifications/
└── administration/
```

### Frontend structure

``` text
apps/web/src/
├── app/
│   ├── (public)/
│   ├── (auth)/
│   ├── (donor)/
│   │   ├── discover/
│   │   ├── saved/
│   │   ├── activity/
│   │   └── profile/
│   └── (recipient)/
│       ├── requests/
│       ├── create/
│       └── dashboard/
├── features/
│   ├── authentication/
│   ├── discovery/
│   ├── requests/
│   ├── funding/
│   └── profiles/
├── components/
├── hooks/
├── lib/
└── styles/
```

The admin interface should be a separately deployed application with
stricter authentication and authorization.

------------------------------------------------------------------------

## 12. Database Design

Use PostgreSQL with UUID primary keys, UTC timestamps, foreign keys,
indexes, constraints, and versioned Flyway migrations.

Store money as integer minor units, not floating-point values. Store the
ISO 4217 currency code alongside every monetary value. Never aggregate
currencies without an explicit exchange-rate conversion and timestamp.

### Core tables

  ---------------------------------------------------------------------
  Table                              Purpose
  ---------------------------------- ----------------------------------
  `users`                            Identity and account status

  `roles`                            Available roles

  `user_roles`                       Role assignments

  `recipient_profiles`               Recipient-specific profile and
                                     verification state

  `donor_profiles`                   Donor preferences and public
                                     display name

  `institutions`                     Education institution records

  `funding_requests`                 Education expense requests and
                                     targets

  `request_documents`                Private evidence references and
                                     review status

  `verification_cases`               Verification workflow

  `verification_decisions`           Reviewer decisions and rationale

  `donor_preferences`                Discovery preferences

  `swipes`                           Current donor/request actions

  `campaigns`                        Direct or group funding
                                     configuration

  `pledges`                          Intended contribution amounts

  `campaign_contributions`           Confirmed campaign contribution
                                     records

  `payments`                         Provider transaction references
                                     and states

  `refunds`                          Refund requests and provider
                                     outcomes

  `webhook_events`                   Idempotent provider event
                                     processing

  `ledger_accounts`                  Financial accounts

  `ledger_transactions`              Immutable financial transaction
                                     headers

  `ledger_entries`                   Debit and credit entries

  `payouts`                          Beneficiary settlement records

  `disputes`                         Payment dispute records

  `user_reports`                     Abuse and suspicious-request
                                     reports

  `audit_events`                     Actor, action, entity, and
                                     timestamp

  `consent_records`                  Policy version and user consent
                                     history
  ---------------------------------------------------------------------

### Flyway migration plan

  -------------------------------------------------------------------------
  Version             File                              Contents
  ------------------- --------------------------------- -------------------
  V1                  `create_identity.sql`             Users, roles,
                                                        recipient and donor
                                                        profiles

  V2                  `create_education_requests.sql`   Institutions,
                                                        requests, documents

  V3                  `create_verification.sql`         Cases and decisions

  V4                  `create_discovery.sql`            Swipes and
                                                        preferences

  V5                  `create_funding.sql`              Campaigns, pledges,
                                                        contributions

  V6                  `create_payments.sql`             Payments, refunds,
                                                        webhook events

  V7                  `create_ledger.sql`               Accounts,
                                                        transactions,
                                                        entries

  V8                  `create_operations.sql`           Reports, disputes,
                                                        audit events
  -------------------------------------------------------------------------

### Important database rules

-   Use database constraints and transactional services for state
    transitions.
-   Preserve financial history; use reversal entries rather than editing
    posted transactions.
-   Prevent edits and deletes of posted ledger entries.
-   Enforce balanced debits and credits per currency through a
    controlled posting service or database procedure.
-   Prevent posting to accounts with a different currency.
-   Use unique provider event IDs and idempotency keys.
-   Add a funding-reservation table and transactional locking to prevent
    overfunding.
-   Keep private storage keys separate from public campaign data.
-   Add payout, reconciliation, consent, retention, and request-state
    history tables before production.

The schema is a foundational design, not a complete production
accounting implementation. Database-level invariants, permissions,
concurrency behavior, and restoration must be tested.

------------------------------------------------------------------------

## 13. Payment State Machine

``` text
Created
   |
   v
Pending provider confirmation
   |
   +------> Failed / Expired
   |
   v
Succeeded
   |
   v
Ledger posting and reconciliation
   |
   +------> Partially refunded
   |
   +------> Refunded
   |
   +------> Disputed
   |
   +------> Reversed
   |
   v
Settled (where applicable)
```

Provider events must be authenticated and reconciled against trusted
provider records. A browser redirect is never proof of payment.

------------------------------------------------------------------------

## 14. API Design

Base URL:

`https://api.example.com/api/v1`

Use HTTPS, JSON, authenticated requests, validation, rate limiting,
consistent error formats, correlation IDs, and cursor-based pagination.

### Identity

  Method   Endpoint               Purpose
  -------- ---------------------- -------------------------------
  POST     `/auth/register`       Create account
  POST     `/auth/verify-email`   Verify email
  POST     `/auth/verify-phone`   Verify phone
  POST     `/auth/session`        Authenticate
  DELETE   `/auth/session`        Revoke current session
  GET      `/me`                  Current user
  PATCH    `/me/profile`          Update allowed profile fields

### Education requests

  --------------------------------------------------------------------------------
  Method         Endpoint                                          Purpose
  -------------- ------------------------------------------------- ---------------
  POST           `/education/requests`                             Create draft

  GET            `/education/requests/{id}`                        Retrieve
                                                                   authorized
                                                                   request

  PATCH          `/education/requests/{id}`                        Update editable
                                                                   draft

  POST           `/education/requests/{id}/documents/upload-url`   Generate
                                                                   private upload
                                                                   authorization

  POST           `/education/requests/{id}/submit`                 Submit for
                                                                   review

  GET            `/education/requests/{id}/status`                 Review status

  POST           `/education/requests/{id}/updates`                Progress update

  GET            `/me/education/requests`                          Recipient's
                                                                   requests
  --------------------------------------------------------------------------------

### Verification

  Method   Endpoint                                    Purpose
  -------- ------------------------------------------- -----------------
  GET      `/admin/verification/cases`                 Review queue
  GET      `/admin/verification/cases/{id}`            Case details
  POST     `/admin/verification/cases/{id}/assign`     Assign reviewer
  POST     `/admin/verification/cases/{id}/decision`   Record decision
  POST     `/education/requests/{id}/appeals`          Submit appeal

### Discovery

  Method   Endpoint                            Purpose
  -------- ----------------------------------- --------------------------
  GET      `/discovery/feed`                   Approved request feed
  GET      `/discovery/feed/{id}`              Public request detail
  POST     `/discovery/requests/{id}/swipes`   Pass or express interest
  GET      `/discovery/saved`                  Saved requests
  DELETE   `/discovery/saved/{id}`             Remove saved request

### Funding and payments

  Method   Endpoint                           Purpose
  -------- ---------------------------------- -------------------------
  POST     `/funding/requests/{id}/pledges`   Create pledge
  GET      `/funding/pledges/{id}`            Retrieve pledge
  POST     `/funding/pledges/{id}/checkout`   Start provider checkout
  GET      `/payments/{id}`                   Payment state
  GET      `/me/payments`                     Payment history
  POST     `/payments/{id}/refund-requests`   Request refund
  POST     `/webhooks/{provider}`             Receive provider events
  GET      `/funding/requests/{id}/summary`   Funding summary

### Administration

  ----------------------------------------------------------------------------
  Method           Endpoint                                   Purpose
  ---------------- ------------------------------------------ ----------------
  GET              `/admin/dashboard`                         Operational
                                                              summary

  GET              `/admin/reports`                           Abuse reports

  POST             `/admin/reports/{id}/resolve`              Resolve report

  GET              `/admin/disputes`                          Dispute queue

  POST             `/admin/disputes/{id}/resolve`             Resolve dispute

  GET              `/admin/reconciliation/batches`            Reconciliation
                                                              history

  POST             `/admin/reconciliation/batches/{id}/run`   Trigger
                                                              reconciliation
  ----------------------------------------------------------------------------

### API conventions and acceptance rules

-   `201 Created` for resource creation.
-   `202 Accepted` for asynchronous work.
-   `409 Conflict` for state conflicts.
-   `422 Unprocessable Entity` for semantically invalid input.
-   `429 Too Many Requests` for rate limiting.
-   Enforce object-level authorization on every resource.
-   Never expose private document URLs to unauthorized clients.
-   Use idempotency keys for payment operations.
-   Never allow users to self-assign privileged roles.
-   Document all contracts in OpenAPI and generate the TypeScript API
    client.

------------------------------------------------------------------------

## 15. Frontend Screen Specifications

### Design direction

-   Premium, calm, and trustworthy.
-   Warm white and charcoal backgrounds.
-   Deep navy primary color.
-   Muted champagne-gold accent.
-   Restrained green for successful states.
-   Readable sans-serif typography such as Inter.
-   Rounded cards and generous whitespace.
-   Smooth but subtle animations.
-   WCAG 2.2 AA accessibility target.

### Screen 1 --- Landing page (P0)

Components:

-   Hero statement and explanation.
-   Browse verified requests.
-   How verification works.
-   Donor and recipient entry points.
-   Institutional partnership section.
-   Trust, safety, and privacy links.

Acceptance criteria:

-   The product is understandable without registration.
-   No private evidence is exposed.
-   Mobile layout works from 320px width upward.
-   Keyboard navigation and accessibility are supported.

### Screen 2 --- Registration and onboarding (P0)

Components:

-   Email and phone registration.
-   Verification.
-   Donor or recipient entry.
-   Country and consent collection.
-   Secure login and account recovery.

Acceptance criteria:

-   Required verification cannot be bypassed.
-   Consent is explicit and versioned.
-   Role changes require appropriate permissions.
-   Recovery flows do not expose whether arbitrary accounts exist.

### Screen 3 --- Donor discovery and swipe feed (P0)

Components:

-   Primary request card.
-   Verification status.
-   Institution and category.
-   Outstanding amount.
-   Funding progress.
-   Due date.
-   Pass, save, and interested actions.
-   Filters and accessible alternatives to gestures.

Acceptance criteria:

-   Only approved requests appear.
-   Mouse, touch, and keyboard interactions work.
-   Cards expose no private documents.
-   Duplicate actions are safely handled.
-   Reduced-motion preferences are respected.

### Screen 4 --- Request details (P0)

Components:

-   Recipient story.
-   Institution information.
-   Verification explanation.
-   Funding breakdown.
-   Remaining target.
-   Privacy-safe contribution history.
-   Report request action.
-   Fund this request CTA.

Acceptance criteria:

-   Funding progress reflects backend-confirmed amounts.
-   Pledges and settled amounts are separate.
-   Verification status is explained.
-   Currency is correctly displayed.

### Screen 5 --- Funding confirmation (P0 for live payments)

Components:

-   Contribution amount.
-   Optional tip, off by default.
-   Funding mode and destination.
-   Fee disclosure.
-   Refund and cancellation terms.
-   Final confirmation.

Acceptance criteria:

-   Total charge is shown before checkout.
-   Explicit confirmation is required.
-   Client-side amount manipulation is rejected.
-   Checkout is initiated only after server-side validation.
-   Redirects cannot independently mark payments successful.

### Screen 6 --- Recipient dashboard (P0)

Components:

-   Active and completed requests.
-   Verification status.
-   Outstanding amount.
-   Confirmed contributions.
-   Settlement history.
-   Correction requests.
-   Support and appeal links.

Acceptance criteria:

-   Recipients see only their own private data.
-   Financial progress matches the ledger.
-   Sensitive donor details are not exposed.
-   Completed requests cannot silently reopen.

### Screen 7 --- Admin verification dashboard (P0)

Components:

-   Pending queue.
-   Request and institution information.
-   Private document viewer.
-   Duplicate and fraud indicators.
-   Decision controls.
-   Reviewer notes and audit history.

Acceptance criteria:

-   Every reviewer action is logged.
-   Reviewers cannot approve their own requests.
-   Sensitive document access is individually authorized and logged.
-   Rejections and appeals are traceable.

### Screen 8 --- Donor impact dashboard (P1)

Components:

-   Contributions.
-   Successfully settled expenses.
-   Pending payment status.
-   Receipts and transaction details.
-   Optional impact summary.

Acceptance criteria:

-   Only confirmed and reconciled payments count as completed funding.
-   Receipts accurately represent transactions.
-   Tax-deductibility claims are displayed only when legally
    substantiated.

------------------------------------------------------------------------

## 16. Security, Privacy, and Trust

### Threats and mitigations

  ---------------------------------------------------------------------
  Threat                             Mitigation
  ---------------------------------- ----------------------------------
  Fake or altered bills              Issuer checks, checksums, manual
                                     review, anomaly flags

  Duplicate requests                 Document hashes, normalized bill
                                     references, duplicate review

  Account takeover                   MFA for sensitive actions, session
                                     rotation, rate limits

  Payment replay                     Signed webhooks, unique event IDs,
                                     idempotency

  Overfunding                        Atomic funding reservations and
                                     locking

  Insider misuse                     Least privilege, separation of
                                     duties, audit logs

  Document leakage                   Private storage, short-lived URLs,
                                     encryption

  Donor manipulation                 Clear evidence, standardized card
                                     information, reporting

  Refund abuse                       Authorization rules and
                                     reconciliation

  AI errors                          Human review, explainable flags,
                                     appeal process
  ---------------------------------------------------------------------

### Privacy principles

-   Public profiles never expose complete bills, bank details, personal
    addresses, medical records, or government identifiers.
-   Strip unnecessary metadata from uploads.
-   Encrypt data in transit and at rest.
-   Manage encryption keys separately from the database.
-   Establish documented retention and deletion schedules.
-   Limit reviewer access to necessary information.
-   Provide consent, privacy notices, access and correction channels,
    and complaint handling.
-   Treat sensitive documents as high risk even when they are not
    public.

### Compliance gates

The actual obligations depend on who receives funds, whether the
platform handles or aggregates money, payment flow, legal entity, and
donor/recipient jurisdictions.

Before accepting live money, obtain qualified advice and written
provider confirmation covering:

1.  Corporate structure and fundraising model.
2.  Payment-provider approval for the actual flow.
3.  Donor, recipient, and institutional due diligence.
4.  Foreign contributions, cross-border settlement, taxes, and receipts.
5.  Privacy, retention, consumer protection, and complaints.
6.  Refunds, fraud losses, disputes, accounting, and records.

For India, review applicable RBI payment-aggregator requirements, the
Digital Personal Data Protection framework and applicable rules, and
FCRA requirements where foreign contributions may be involved. Do not
assume that international card acceptance makes cross-border donations
permissible.

**Do not operate an unapproved wallet or hold pooled customer funds
while the legal structure is unresolved.** Validate the discovery and
verification experience using fictional data, non-binding pledges, and
provider sandbox transactions first.

------------------------------------------------------------------------

## 17. Prioritized Engineering Backlog

Priority definitions:

-   **P0:** Blocks private beta or creates unacceptable risk if missing.
-   **P1:** Required for a complete pilot.
-   **P2:** Important enhancement.
-   **P3:** Future expansion.

### Epic A --- Platform foundation

  -----------------------------------------------------------------------
  ID                Feature           Priority          Acceptance
                                                        criteria
  ----------------- ----------------- ----------------- -----------------
  PLAT-001          Monorepo setup    P0                Apps build
                                                        independently;
                                                        contracts
                                                        versioned

  PLAT-002          CI/CD             P0                Tests, lint,
                                                        migrations,
                                                        security checks
                                                        on PRs

  PLAT-003          Environments      P0                Dev, staging,
                                                        production
                                                        secrets isolated

  PLAT-004          Migrations        P0                Repeatable
                                                        migrations and
                                                        rollback strategy

  PLAT-005          Observability     P0                Structured logs,
                                                        metrics, traces,
                                                        correlation IDs

  PLAT-006          Feature flags     P1                Feature control
                                                        by environment
                                                        and cohort
  -----------------------------------------------------------------------

### Epic B --- Identity

  -----------------------------------------------------------------------
  ID                Feature           Priority          Acceptance
                                                        criteria
  ----------------- ----------------- ----------------- -----------------
  ID-001            Registration      P0                Email/phone
                                                        verification

  ID-002            Authentication    P0                Secure sessions,
                                                        expiry,
                                                        revocation

  ID-003            MFA               P0                Required for
                                                        privileged admin
                                                        actions

  ID-004            Roles             P0                Server-side
                                                        permission
                                                        enforcement

  ID-005            Profile           P1                Authorized
                    management                          profile editing

  ID-006            Account deletion  P1                Legal
                                                        retention-aware
                                                        deletion workflow
  -----------------------------------------------------------------------

### Epic C --- Education requests

  -----------------------------------------------------------------------
  ID                Feature           Priority          Acceptance
                                                        criteria
  ----------------- ----------------- ----------------- -----------------
  EDU-001           Create request    P0                Draft supports
                                                        INR tuition
                                                        expenses

  EDU-002           Institution       P0                Institution and
                    details                             fee references
                                                        captured

  EDU-003           Bill upload       P0                Private uploads
                                                        and scanning

  EDU-004           Submit for review P0                Controlled
                                                        submission and
                                                        correction
                                                        workflow

  EDU-005           Request lifecycle P0                Valid state
                                                        transitions
                                                        enforced

  EDU-006           Partial funding   P0                Remaining need
                                                        recalculated
                                                        transactionally

  EDU-007           Funding           P0                No contributions
                    completion                          beyond eligible
                                                        target

  EDU-008           Progress updates  P1                Non-financial
                                                        updates recorded
  -----------------------------------------------------------------------

### Epic D --- Verification

  -----------------------------------------------------------------------
  ID                Feature           Priority          Acceptance
                                                        criteria
  ----------------- ----------------- ----------------- -----------------
  TRUST-001         Review queue      P0                Reviewers see
                                                        pending cases

  TRUST-002         Manual            P0                Approve, reject,
                    verification                        request
                                                        corrections

  TRUST-003         Duplicate         P0                Potential
                    detection                           duplicates
                                                        flagged

  TRUST-004         Audit trail       P0                Actor, time, and
                                                        rationale
                                                        recorded

  TRUST-005         OCR extraction    P1                Suggestions only;
                                                        no automatic
                                                        approval

  TRUST-006         Issuer            P1                Institution
                    verification                        checks recorded

  TRUST-007         Appeals           P1                Controlled appeal
                                                        workflow
  -----------------------------------------------------------------------

### Epic E --- Discovery

  -----------------------------------------------------------------------
  ID              Feature           Priority        Acceptance criteria
  --------------- ----------------- --------------- ---------------------
  MATCH-001       Feed              P0              Only approved
                                                    requests

  MATCH-002       Swipe gestures    P0              Touch, mouse,
                                                    keyboard

  MATCH-003       Shortlist         P0              Saved requests
                                                    retrievable

  MATCH-004       Filters           P1              Category, target, due
                                                    date, institution

  MATCH-005       Ranking           P1              No
                                                    sensitive-attribute
                                                    targeting

  MATCH-006       Recommendations   P2              Permitted preference
                                                    and engagement
                                                    signals
  -----------------------------------------------------------------------

### Epic F --- Funding and payments

  -----------------------------------------------------------------------
  ID                Feature           Priority          Acceptance
                                                        criteria
  ----------------- ----------------- ----------------- -----------------
  PAY-001           Pledges           P0                Separate from
                                                        confirmed
                                                        payments

  PAY-002           Checkout          P0                Unique
                                                        idempotency key

  PAY-003           Webhooks          P0                Signature
                                                        verification and
                                                        duplicate
                                                        protection

  PAY-004           Double-entry      P0                Every posted
                    ledger                              transaction
                                                        balances

  PAY-005           Reservations      P0                Concurrent donors
                                                        cannot overfund

  PAY-006           Refunds           P0                Original payment
                                                        referenced

  PAY-007           Reconciliation    P0                Discrepancies
                                                        surfaced

  PAY-008           Direct issuer     P1                Approved
                    payment                             beneficiary flow

  PAY-009           Group             P1                Multiple donors
                    contributions                       fund a campaign

  PAY-010           Payout workflow   P1                Beneficiary and
                                                        settlement
                                                        verified
  -----------------------------------------------------------------------

### Epic G --- Operations

  ID        Feature           Priority   Acceptance criteria
  --------- ----------------- ---------- ------------------------------------
  OPS-001   Admin dashboard   P0         Role-restricted operations
  OPS-002   User reports      P0         Suspicious requests reportable
  OPS-003   Disputes          P0         Owner, state, and resolution
  OPS-004   Notifications     P1         Reliable email and in-app delivery
  OPS-005   Support tooling   P1         Permitted metadata access
  OPS-006   Impact reports    P2         Verified outcomes

### Epic H --- Security and compliance

  -----------------------------------------------------------------------
  ID                Feature           Priority          Acceptance
                                                        criteria
  ----------------- ----------------- ----------------- -----------------
  SEC-001           Authorization     P0                Object-level
                    testing                             controls tested

  SEC-002           Encryption        P0                Transit and
                                                        at-rest
                                                        encryption

  SEC-003           File security     P0                Scanning, type
                                                        validation,
                                                        private access

  SEC-004           Rate limits       P0                Sensitive
                                                        endpoints
                                                        protected

  SEC-005           Threat model      P0                Abuse cases
                                                        documented and
                                                        mitigated

  SEC-006           Privacy controls  P0                Consent,
                                                        retention,
                                                        access,
                                                        correction,
                                                        deletion

  SEC-007           Penetration test  P0                Critical/high
                                                        findings
                                                        remediated

  SEC-008           Disaster recovery P0                Backup
                                                        restoration
                                                        tested
  -----------------------------------------------------------------------

### Definition of Done

Every P0 story must have:

-   Implementation and code review.
-   Automated tests.
-   API documentation.
-   Appropriate logs and metrics.
-   Security review where relevant.
-   Staging deployment.
-   Product acceptance.

------------------------------------------------------------------------

## 18. Production Infrastructure

### AWS deployment model

``` text
CloudFront + AWS WAF
        |
        v
Next.js web frontend / Admin frontend
        |
        v
Application Load Balancer
        |
        v
ECS Fargate
  ├── Spring Boot API
  └── Background workers
        |
        +---- RDS PostgreSQL
        +---- Private S3 storage
        +---- SQS queues
        +---- Secrets Manager
        +---- CloudWatch / OpenTelemetry
```

### Environment strategy

  Environment   Purpose
  ------------- ------------------------------------------
  Development   Daily development and integration
  Staging       Production-like QA and payment sandbox
  Production    Approved live-user and payment workflows

### Infrastructure requirements

-   Terraform-managed infrastructure.
-   Separate AWS accounts for production and non-production where
    practical.
-   Private database subnets.
-   Least-privilege IAM.
-   Secrets Manager for credentials.
-   S3 versioning, encryption, and restrictive policies.
-   Automated database backups and point-in-time recovery.
-   Health checks and deployment rollback.
-   Centralized security logs.
-   Disaster recovery exercises.

### Initial service objectives

  Metric                                Proposed target
  ------------------------------------- --------------------------------
  Availability                          99.9% monthly
  Ordinary read API latency             p95 below 500 ms
  Recovery Point Objective              15 minutes
  Recovery Time Objective               4 hours
  Critical unresolved vulnerabilities   Zero
  Webhook processing                    Idempotent and recoverable
  Reconciliation                        Daily, with discrepancy alerts

These are proposed objectives, not guarantees.

------------------------------------------------------------------------

## 19. Testing Strategy

  ---------------------------------------------------------------------
  Test layer                         Scope
  ---------------------------------- ----------------------------------
  Unit                               Domain rules, validation,
                                     calculations

  Integration                        PostgreSQL, repositories,
                                     transaction boundaries

  Contract                           Frontend/backend compatibility

  E2E                                Registration through request and
                                     funding

  Security                           Authorization, injection, session
                                     handling, upload abuse

  Concurrency                        Simultaneous pledges and
                                     overfunding

  Payment simulation                 Duplicate webhooks, delayed
                                     events, failures, refunds

  Disaster recovery                  Restore database and resume
                                     processing

  Accessibility                      Keyboard, screen readers, reduced
                                     motion
  ---------------------------------------------------------------------

### Mandatory critical scenarios

1.  **Concurrent funding:** Two donors attempt to fund the final ₹5,000
    simultaneously. The system must never accept more than the eligible
    outstanding amount.
2.  **Duplicate webhook:** The same successful payment event is sent
    five times. Only one financial posting occurs.
3.  **Altered bill:** A recipient changes the amount after verification.
    The request returns to review.
4.  **Unauthorized access:** A donor tries to retrieve another
    recipient's private document. Access is denied and logged.
5.  **Partial refund:** A ₹5,000 payment is partially refunded by
    ₹2,000. The ledger records the reversal and correctly accounts for
    the remaining ₹3,000.

Automate these scenarios before the first live-money release.

------------------------------------------------------------------------

## 20. Delivery Roadmap

A staffed-team planning target is approximately 20 weeks, subject to
provider onboarding, legal review, and scope.

  Milestone           Target Deliverable
  ----------- -------------- ---------------------------------------------------
  M0              Weeks 1--2 Product specification, architecture, threat model
  M1              Weeks 3--5 Identity, profiles, education requests
  M2              Weeks 6--8 Verification, admin operations, discovery
  M3             Weeks 9--12 Pledges, payment sandbox, ledger
  M4            Weeks 13--16 Security hardening, reconciliation, private beta
  M5            Weeks 17--20 Approved live-payment pilot and controlled launch

### Sprint 1 --- Platform foundation

Owners:

-   Tech lead: architecture and domain boundaries.
-   Backend engineer 1: Spring Boot and identity.
-   Backend engineer 2: PostgreSQL and Flyway.
-   Frontend engineer 1: design system and app shell.
-   Frontend engineer 2: recipient onboarding.
-   DevOps: AWS, Terraform, CI/CD.
-   QA: test framework and smoke tests.

Exit criteria:

-   All developers run the project locally.
-   CI pipelines pass.
-   Development and staging exist.
-   Authentication works end to end.
-   Migrations run successfully.
-   Basic frontend routes are deployed.

### Sprint 2 --- Education requests

Owners:

-   Backend engineer 1: recipient profiles and request CRUD.
-   Backend engineer 2: document storage and upload authorization.
-   Frontend engineer 1: recipient request creation.
-   Frontend engineer 2: request dashboard and status.
-   DevOps: storage policies and scanning.
-   QA: lifecycle and access tests.

Exit criteria:

-   Recipient can create a tuition request.
-   Recipient can upload supporting documents.
-   Documents remain private.
-   Request can be submitted for review.
-   Unauthorized-access tests pass.
-   Admin can see a pending request.

------------------------------------------------------------------------

## 21. Project Management

Use GitHub Projects with this workflow:

``` text
Backlog
   |
   v
Ready
   |
   v
In Progress
   |
   v
In Review
   |
   v
QA
   |
   v
Done
```

Every ticket should contain:

-   Unique issue ID.
-   Epic and priority.
-   Description and user story.
-   Acceptance criteria.
-   Dependencies.
-   Test requirements.
-   Assigned owner.
-   Definition of Done.

Use Architecture Decision Records (ADRs) for payment flows,
authentication, ledger design, data retention, and jurisdiction-specific
decisions.

------------------------------------------------------------------------

## 22. Go/No-Go Criteria

Before expanding beyond the first cohort, evaluate:

  ---------------------------------------------------------------------
  Metric                             Decision criterion
  ---------------------------------- ----------------------------------
  Request verification               Every public request has passed
                                     required review

  Payment reconciliation             All pilot transactions accounted
                                     for; discrepancies investigated

  Privacy and access                 No unresolved critical
                                     authorization or data-exposure
                                     issues

  Funding outcomes                   Successful settlement of
                                     legitimate expenses demonstrated

  Donor retention                    Evidence that donors return to
                                     contribute

  Recipient experience               Recipients understand process,
                                     privacy, and payout timing

  Financial sustainability           Credible path to covering variable
                                     costs

  Compliance                         Written approval for the intended
                                     launch structure and payment flow
  ---------------------------------------------------------------------

Set numerical conversion thresholds after interviews and pilot data.
Avoid arbitrary targets before donor behavior is understood.

------------------------------------------------------------------------

## 23. Immediate Execution Plan

Run three parallel tracks.

### Engineering

1.  Create the monorepo.
2.  Configure Spring Boot, Next.js, PostgreSQL, and Flyway.
3.  Implement authentication and recipient profiles.
4.  Implement education request creation and private uploads.
5.  Build the verification dashboard.
6.  Build the donor swipe feed.
7.  Implement pledges and sandbox payment integration.
8.  Add ledger, reconciliation, and refund workflows.
9.  Harden and test the platform.

### Business and compliance

1.  Confirm first-market strategy.
2.  Interview potential recipients, donors, and institutions.
3.  Define eligible education expenses.
4.  Obtain legal and payment-provider guidance.
5.  Define the approved fund flow.
6.  Establish refund, complaint, and support procedures.

### Customer discovery

1.  Recruit prospective students.
2.  Interview donors and alumni.
3.  Test whether the swipe experience creates meaningful interest.
4.  Validate trust indicators and verification explanations.
5.  Test whether recipients are comfortable with the information shown
    publicly.
6.  Measure willingness to make repeat contributions.

### First vertical slice

Build this end-to-end workflow before adding advanced features:

**Recipient registers → submits tuition bill → admin verifies it →
approved bill appears in donor swipe feed.**

This creates the first functioning product without prematurely taking on
live payment risk.

------------------------------------------------------------------------

## 24. Recommended Next Engineering Deliverables

Once this plan is approved, create the following artifacts:

1.  GitHub-ready monorepo.
2.  Spring Boot application with domain modules.
3.  Flyway SQL migrations and integration tests.
4.  OpenAPI specification.
5.  Generated TypeScript API client.
6.  Next.js application shell and core screens.
7.  Docker Compose local development environment.
8.  CI workflows and automated tests.
9.  Terraform foundation for AWS staging.
10. Operational runbooks and security review checklist.

The first release should prove that a verified tuition request can
safely move from recipient submission to donor discovery. Payments
should be enabled only after the financial, legal, and operational
foundations are ready.

------------------------------------------------------------------------

## 25. Guiding Principles

1.  Trust is the product, not a feature.
2.  A pledge is not a payment.
3.  A payment is not settled until properly reconciled.
4.  A verified bill is not permission to expose someone's private life.
5.  AI can assist verification, but humans remain accountable for
    consequential decisions.
6.  Recipients should not pay to access assistance.
7.  The platform should never claim tax benefits without legal
    substantiation.
8.  Financial records must be auditable and immutable after posting.
9.  Build for international expansion, but launch within a validated
    legal and operational boundary.
10. Measure successful expenses settled, not the number of swipes.

**The long-term ambition:** make helping someone financially as easy as
making a payment, while preserving the dignity of the person receiving
help and the trust of the person giving it.

# CareConnect

First deployable release of the CareConnect care coordination platform: a Java API, an Angular portal, Azure infrastructure, and GitHub Actions workflows.

> **Do not use with real patient data yet.** See [Before real patient data](#before-real-patient-data).

## What is included

| Area | Included |
|---|---|
| Screens | The six design screens (worklist, patient record, discharge summary review, referral management, bed and staffing forecast, model governance), plus Note review and Audit. Each person sees only the screens for their roles |
| Patient record | Alerts, consent by purpose, care team, timeline, medicines, next steps, latest signed summary, and readmission risk with reasons and clinician feedback |
| Access control | Role, care relationship, and consent checks; audited emergency access; privacy officer review of each emergency access; audit trail search |
| Referrals | Board by stage; AI-extracted summary with its source; service suggestions from eligibility rules and wait times; accept and book, change, or request information |
| Care tasks | Worklist with reason, team, and risk; team load; interface status; automatic GP follow-up task after discharge |
| AI drafts | Cited, sectioned discharge drafts with source highlighting, traced-fact count, and items to check; clinician signature required; kill switch, off by default |
| Analytics | Worklist measures (follow-up within 7 days, discharge pressure, triage wait); hourly bed census; 7-day bed forecast with an 80% range and its measured accuracy; ward pressure; nursing gaps; Power BI views (`rpt_fact_*`) |
| Model governance | Registry of model versions; subgroup performance with review and watch status from the release-gate limits; clinician feedback rates; approvals and evidence; a suspend switch that hides a model's output from every care screen. Only live models are shown to care teams |
| Sign-in | Production: Microsoft Entra ID with MFA. Dev and test can instead use CareConnect accounts (`auth_mode = "database"`): bcrypt-hashed passwords, roles per account, lockout after 5 wrong passwords, temporary passwords that must be changed, and a Users screen for administrators. Terraform refuses database sign-in in production |
| Demo data | Synthetic patients, wards, referrals, scores, and model evidence for local and dev, rebuilt each day. Terraform refuses it in production |
| Integration | HL7 v2 ADT A01 and A03 over HTTPS, with raw message storage and duplicate detection |
| Delivery | Dockerfiles, Terraform, CI and deployment pipelines |

### Not built yet

These parts of the design document are not in this release:

- FHIR store (Azure Health Data Services), and pathology, GP, pharmacy, and My Health Record feeds
- Patient matching beyond exact MRN, and terminology mapping
- The de-identified analytics platform (Databricks, Delta Lake, MLflow). The bed forecast here is a simple seasonal baseline (`cc-capacity baseline-1`) computed in the API. Risk scores are loaded through `PUT /api/v1/risk-scores` by a scoring job that does not exist yet, so in dev they come from demo data
- Front Door + WAF, a hub network, ExpressRoute, and disaster recovery region
- SMS reminders, ServiceNow incidents, and alert rules
- Consent changes from the portal (the consent API exists)

### Differences from the design document

| Design | This release | Why |
|---|---|---|
| Java 25 | Java 21 LTS | Matches the toolchain this was built and tested on |
| Microservices on AKS | One modular API on Azure Container Apps | Much less to operate for a first release; modules can split later |
| Secrets in Key Vault | No Key Vault | The release has no secrets. It uses managed identity and Entra sign-in |
| Entra ID with MFA for everyone | Dev signs in with CareConnect accounts, which have no MFA | Requested for dev, so testers need no Entra accounts. Production must use Entra (enforced by Terraform) |
| Australian regions only | Dev, test, and prod run in Australia East. The shared registry and Terraform state are in UK South, and UK regions are allowed for non-production | A Free Trial subscription cannot create Azure SQL in UK regions. Terraform rejects UK regions for prod |

## Run locally

Needs Java 21 and Node 24. Local mode uses an in-memory database, synthetic demo data, and a user picker in place of sign-in. Pick **All roles (demo walkthrough)** to see every screen.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# In a second terminal
cd frontend
npm ci
npx ng serve
```

Open http://localhost:4200.

## Test

```bash
cd backend && ./mvnw verify
cd frontend && CI=1 npx ng test --watch=false
cd infra/terraform && terraform init -backend=false && terraform validate
```

## Deploy

All infrastructure and application deployments run from GitHub Actions. Steps 1 to 3 are one-time setup done by an administrator, because they create the identity that GitHub Actions signs in to Azure with.

### 1. Create shared Azure resources

Run once as an Azure administrator:

```bash
./infra/bootstrap.sh <short-unique-prefix>
```

### 2. Register two applications in Microsoft Entra ID

**API (`careconnect-api`)**
- Set the Application ID URI to `api://<api-client-id>`.
- Expose a scope named `access_as_user`.
- Set `accessTokenAcceptedVersion` to `2` in the manifest.
- Create app roles: `CLINICIAN`, `COORDINATOR`, `TRIAGE`, `PRIVACY`, `MANAGER`, `SAFETY`, and `INTEGRATION`.

**Portal (`careconnect-portal`)**
- Add a single-page application platform. The redirect URI is the `portal_url` Terraform output, so add it after the first deployment.
- Grant it the `access_as_user` scope.
- Create the same app roles if the portal should adapt its buttons to them, then assign users to both applications.

### 3. Set up GitHub Actions

GitHub reads workflows only from the repository root, so `CareConnect` must be the root of the repository.

| Workflow | Runs when | What it does |
|---|---|---|
| `ci.yml` | Pull requests to `main` | Tests and validates, with no Azure access |
| `terraform.yml` (Infrastructure) | Merges that change `infra/terraform`, or by hand | Plans and applies to dev, then test. Production is by hand only |
| `build-images.yml` | By hand, or called by Deploy app | Builds both images in the registry |
| `deploy-app.yml` (Deploy app) | Merges that change `backend` or `frontend`, or by hand | Runs CI and builds once, then deploys to dev and test. Production is by hand only |
| `environment-power.yml` (Start or stop environment) | Every day at 17:00 UK time for dev, or by hand | Stops the dev apps every evening to save cost. Start or stop any environment by hand |

1. Create a Microsoft Entra application for GitHub, and give it these roles:
   - **Contributor** and **User Access Administrator** on the subscription (Terraform creates a role assignment)
   - **AcrPush** on the registry (images are built on the runner and pushed)
   - **Storage Blob Data Contributor** on the state storage account
2. Add a federated credential to it for each GitHub environment below, with the subject `repo:<owner>/<repo>:environment:<environment name>`. If the repository uses immutable OIDC subjects, use the prefix from `gh api repos/<owner>/<repo>/actions/oidc/customization/sub` instead, such as `repo:<owner>@<id>/<repo>@<id>:environment:<environment name>`.
3. Create these GitHub environments:

   | Environment | Purpose | Set up |
   |---|---|---|
   | `careconnect-build` | Builds images | Restrict to `main` |
   | `careconnect-dev-plan`, `careconnect-test-plan`, `careconnect-prod-plan` | Terraform plans | Add the variables in the next table |
   | `careconnect-dev`, `careconnect-test` | Terraform apply and app deployment | Restrict to `main` |
   | `careconnect-prod` | Terraform apply and app deployment | Restrict to `main`, and add required reviewers |

   Variables for each `-plan` environment:

   | Variable | Example |
   |---|---|
   | `ALLOWED_CIDRS` | `["203.0.113.0/24"]`. Dev and test may use `["0.0.0.0/0"]`; prod rejects it until Front Door + WAF is added |
   | `ENTRA_API_CLIENT_ID` | API client ID |
   | `ENTRA_SPA_CLIENT_ID` | Portal client ID |
   | `AI_ENABLED` | `false` |

4. Add these repository variables: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, and the four values printed by `bootstrap.sh`. No secrets are needed.
5. Require the CI checks on `main` with a branch protection rule.

**First deployment of each environment:**

1. Run **Build images**, and copy the image tag from its summary. You only need to do this once.
2. Run **Infrastructure** with the environment, `apply`, and that image tag.
3. Add the `portal_url` output as the portal's redirect URI in Entra.

**After that:**

- Merges deploy to dev and test automatically.
- To deploy production, run **Deploy app** on `main` and select **Deploy to production after test**.
- To change production infrastructure, run **Infrastructure** with `plan`, read the summary, then run it with `apply` and have a reviewer approve.
- Infrastructure runs keep the images already running, so they don't undo an app deployment.

### 4. Run it

Merging to `main` deploys to dev and test. Production runs only if you start **Deploy app** with **Deploy to production after test** selected, and its reviewers approve.

## Operations

- **CareConnect accounts (dev):** sign in as `admin` with the first password from `terraform output -raw bootstrap_admin_password` (or the API's `bootstrap-admin-password` Container App secret), and change it. Then add people on the **Users** screen, giving each a temporary password and only the roles they need. The `admin` account is created only when no enabled administrator exists.

- **Demo data:** dev sets `demo_data = true` in `infra/terraform/env/dev.tfvars`. The API rebuilds the synthetic data each day (site time zone), and only ever touches rows whose IDs start with `demo-`. A signed-in person with no demo patients sees **Join the demo care team** at the top of the portal; joining is audited.
- **Roles:** the API and portal app registrations need two more app roles for the new screens: `MANAGER` (bed and staffing forecast) and `SAFETY` (model governance).
- **Suspend a risk model:** on Model governance, give a reason and select **Suspend model**. Its scores disappear from every care screen at once.

- **Turn off AI drafting:** set `AI_ENABLED` to `false` and redeploy. Other screens keep working.
- **Roll back:** rerun the deployment for the last good commit. Database migrations only move forward, so a release with a schema change needs a forward fix.
- **Logs:** Container Apps send logs to the environment's Log Analytics workspace.
- **Save cost out of hours:** dev apps stop every day at 17:00 UK time. To start them, run **Start or stop environment** with `dev` and `start`. Stopping affects only app compute. The dev database is serverless (`GP_S_Gen5_1`): it pauses after 60 idle minutes and then bills only for storage, and the first connection after a pause wakes it in about a minute. Log Analytics and the network are still billed. Test and prod keep an always-on database (`database_sku`). A deployment starts stopped apps. Stopping prod needs its reviewers to approve.
- **Sending HL7:** POST messages to `/api/v1/integration/hl7` through the portal address, using a token with the `INTEGRATION` role.

### Enable a language model

The default draft generator quotes source notes and uses no model. To use Azure OpenAI, after safety approval:

1. Deploy a model in an Australian region with private access, and give the app identity the **Cognitive Services OpenAI User** role.
2. Set `CARECONNECT_AI_PROVIDER=azure-openai`, `AZURE_OPENAI_ENDPOINT`, and `AZURE_OPENAI_DEPLOYMENT` on the API in `infra/terraform/main.tf`.

## Before real patient data

Deployment does not make this safe for clinical use. These must be closed first:

**Governance**
- [ ] Clinical safety case approved by the Clinical Safety Officer
- [ ] Privacy impact assessment completed and actions closed
- [ ] Regulatory classification of each AI feature (TGA medical device rules)
- [ ] Independent penetration test

**Technical**
- [ ] Front Door + WAF added in place of IP allow-listing
- [ ] Separate database roles: the app identity is currently the SQL administrator, so a compromised app could change the audit table and its trigger
- [ ] Audit log exported to immutable storage or Sentinel
- [ ] Disaster recovery designed and rehearsed
- [ ] Alerts on failed HL7 messages (they are stored as `FAILED`, but nothing notifies anyone yet)
- [ ] Reprocessing tool for failed HL7 messages
- [ ] Site time zone for HL7 times confirmed (times without a zone are treated as UTC)
- [ ] Care team feed: nothing fills `care_team_member` yet, so staff have no access until this is loaded

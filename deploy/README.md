# Deploying EventHub to AWS EC2 with Kubernetes (k3s)

One EC2 instance runs **k3s** (a lightweight, certified Kubernetes). Inside it:

```
                    Internet
                       │  :80 / :443
              ┌────────▼─────────┐
              │ Traefik ingress  │  (built into k3s)
              └───┬──────────┬───┘
         /api/*   │          │   everything else
        ┌─────────▼──┐   ┌───▼──────────┐
        │ backend pod│   │ frontend pod │   nginx + built React app
        │ Spring Boot│   └──────────────┘
        └─────┬──────┘
              │
        ┌─────▼──────┐   ┌──────────────────────┐
        │ postgres   │◄──│ postgres-backup      │  daily pg_dump, keeps 7
        │ (+ volume) │   │ CronJob              │
        └────────────┘   └──────────────────────┘
```

- **frontend** and **backend** are the two application pods. PostgreSQL runs as a third pod with a persistent volume (or use AWS RDS instead; see below).
- Everything is served from **one address**, so the browser never makes cross-origin calls and the login cookie works.
- Spring Boot's `/actuator` endpoints are **not** exposed publicly; Kubernetes uses them internally for health checks.
- Images are built **on the instance** and loaded straight into k3s, so no container registry is needed.

Files: `deploy/k8s/*.yaml` (manifests), `deploy/ec2/deploy.sh` (install + deploy, safe to re-run), `deploy/smoke-test.sh`.

---

## 1. Instance requirements

| | Minimum | Notes |
|---|---|---|
| Type | **t3.micro** (1 GB) with prebuilt images · **t3.small** (2 GB) to build on the server | On 1 GB, use `IMAGES=ghcr` (images built by GitHub Actions, see below). Building on the server needs ~2 GB. |
| OS | Amazon Linux 2023 | (the `ec2-user` login) |
| Disk | 20 GB gp3 | images + database + backups |
| Network | public IPv4 address | |

**Security group (inbound rules):**

| Port | Source | Why |
|---|---|---|
| 80 (HTTP) | 0.0.0.0/0 | the website (and Let's Encrypt verification) |
| 443 (HTTPS) | 0.0.0.0/0 | the website over HTTPS |
| 22 (SSH) | EC2 Instance Connect only / your IP | browser terminal access. Don't open to 0.0.0.0/0 |

EC2 console → Instances → your instance → **Security** tab → security group → **Edit inbound rules**.

## 2. Get the code onto the instance

In the EC2 Instance Connect terminal:

```bash
sudo dnf install -y git
git clone https://github.com/<you>/<repo>.git eventhub
cd eventhub
```

(For a private repository, use a GitHub fine-grained personal access token with read-only "Contents" access as the password when git asks.)

## 3. Deploy

### Prebuilt images (any size, required on 1 GB / t3.micro)

Every push to `main` runs **GitHub Actions** (`.github/workflows/ci.yml`): backend tests, frontend lint/build, and then it publishes both images to GitHub Container Registry:

- `ghcr.io/likhithuc/eventhub-backend:main`
- `ghcr.io/likhithuc/eventhub-frontend:main`

The instance only downloads them, so no Docker or Java build is needed on the server. Add `IMAGES=ghcr` to any deploy command below:

```bash
IMAGES=ghcr LE_EMAIL=you@example.com ./deploy/ec2/deploy.sh
```

This uses the `deploy/k8s-ghcr` overlay with lean memory settings (backend ~300 MiB within a 560 MiB limit). It fits a 1 GB instance for a demo, but start-up is slower and there is little headroom; t3.small is better for real traffic.

> Because this repository is public, the images are public too, so the server can download them without logging in. If you ever make the repository private, either make the packages public (GitHub → profile → **Packages** → package → **Package settings → Change visibility**) or the pods will fail with `ErrImagePull`.

To update: push to `main`, wait for the CI run to finish (green check on GitHub), then on the server run `cd ~/eventhub && git pull && IMAGES=ghcr LE_EMAIL=… ./deploy/ec2/deploy.sh`.

### Building on the server (2 GB+ instances)

**HTTPS (recommended).** Get a free Let's Encrypt certificate. Without your own domain, the site gets an address like `https://13-51-10-20.sslip.io`, which maps to your instance's IP:

```bash
LE_EMAIL=you@example.com ./deploy/ec2/deploy.sh
```

With your own domain (create a DNS `A` record pointing at the instance's public IP first):

```bash
LE_EMAIL=you@example.com DOMAIN=events.example.com ./deploy/ec2/deploy.sh
```

**HTTP only (quick test):**

```bash
./deploy/ec2/deploy.sh
```

The first run installs Docker and k3s, adds 2 GB of swap, builds both images (about 5–10 minutes on a t3.small), generates secrets, starts everything and runs the smoke test. At the end it prints the site URL.

**Logins:** the first run creates an admin and an organizer account with random passwords:

```bash
cat ~/eventhub-credentials.txt
```

Change these passwords or remove the accounts before inviting real users. Set `SEED_DEMO_EVENTS=false` on the first deploy if you don't want the sample events.

## 4. Update to a new version

```bash
cd ~/eventhub && git pull && LE_EMAIL=you@example.com ./deploy/ec2/deploy.sh
```

Secrets and database data are kept. Database migrations (Flyway) run automatically when the new backend starts.

## 5. Day-to-day commands

```bash
alias k='sudo k3s kubectl -n eventhub'
k get pods                     # all should be Running / READY 1/1
k logs deploy/backend -f       # backend logs
k logs deploy/frontend
k top pods                     # memory / CPU use
k get certificate              # HTTPS certificate status (READY=True)
./deploy/smoke-test.sh https://<your-address>
```

## 6. Backups and restore

A CronJob runs `pg_dump` every day at 02:30 UTC and keeps the last 7 dumps on their own volume.

```bash
# Back up right now and check it worked
k create job --from=cronjob/postgres-backup backup-now
k logs job/backup-now -f          # "backup written: /backups/eventhub-....dump"

# List backups
k run ls-backups --rm -i --restart=Never --image=postgres:17-alpine \
  --overrides='{"spec":{"volumes":[{"name":"b","persistentVolumeClaim":{"claimName":"postgres-backups"}}],"containers":[{"name":"c","image":"postgres:17-alpine","command":["ls","-lh","/b"],"volumeMounts":[{"name":"b","mountPath":"/b"}]}]}}'
```

**Restore** (replaces the current data; test this before you need it):

```bash
k scale deploy/backend --replicas=0
k run restore --rm -i --restart=Never --image=postgres:17-alpine \
  --overrides='{"spec":{"volumes":[{"name":"b","persistentVolumeClaim":{"claimName":"postgres-backups"}}],"containers":[{"name":"c","image":"postgres:17-alpine","env":[{"name":"PGPASSWORD","valueFrom":{"secretKeyRef":{"name":"eventhub-secrets","key":"DB_PASSWORD"}}}],"command":["pg_restore","-h","postgres","-U","eventhub","-d","eventhub","--clean","--if-exists","/b/eventhub-YYYYMMDD-HHMMSS.dump"],"volumeMounts":[{"name":"b","mountPath":"/b"}]}]}}'
k scale deploy/backend --replicas=1
```

These backups live on the same machine. For real data, also copy them off the instance, e.g. to S3: attach an IAM role with `s3:PutObject` on one bucket to the instance, then copy from the `postgres-backups` volume (`/var/lib/rancher/k3s/storage/`) with `aws s3 cp`. Or use **AWS RDS** with automated backups (below).

## 7. Monitoring and cost control (AWS console)

- **Budget alert:** Billing → Budgets → Create budget → monthly cost budget (e.g. $30) with email alerts at 80% and 100%. Note that a budget is an alert, not a hard spending cap.
- **Instance health alarm:** EC2 → your instance → Status checks → *Create status check alarm* (email when the instance is unhealthy).
- **CPU alarm:** CloudWatch → Alarms → EC2 `CPUUtilization` > 80% for 15 minutes.
- **Uptime:** run `./deploy/smoke-test.sh https://<address>` from anywhere, or use an external uptime checker against `/api/v1/health`.

## 8. Optional: use AWS RDS instead of the postgres pod

1. Create an RDS PostgreSQL 17 instance in the **same VPC**, not publicly accessible, whose security group allows port 5432 only from the EC2 instance's security group. Enable automated backups.
2. Remove `postgres.yaml` and `backup.yaml` from `deploy/k8s/kustomization.yaml`.
3. Edit `deploy.sh`: set `DB_URL` to `jdbc:postgresql://<rds-endpoint>:5432/eventhub`, set `DB_USERNAME`, and put the RDS password in the `DB_PASSWORD` key of the `eventhub-secrets` secret.

## 9. Email

Booking emails are **off** by default in this deployment (`MAIL_ENABLED=false`), because there is no mail server yet. To turn them on, use Amazon SES (or another SMTP provider): add `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT=587`, `MAIL_USERNAME` and `MAIL_FROM` to the config map, and `MAIL_PASSWORD` to the secret. Then re-run the deploy.

## 10. Tear down

```bash
/usr/local/bin/k3s-uninstall.sh     # removes k3s, all pods and ALL DATA on this instance
```

Then stop or terminate the instance in the EC2 console so it stops costing money.

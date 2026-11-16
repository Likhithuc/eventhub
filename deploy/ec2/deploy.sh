#!/usr/bin/env bash
# Deploys EventHub to a single EC2 instance running k3s (lightweight Kubernetes).
# Safe to re-run: it updates images and config and keeps existing secrets and data.
#
# Run from the repository root on the instance:
#     ./deploy/ec2/deploy.sh
# Options (environment variables):
#     LE_EMAIL=you@example.com   enable HTTPS with a free Let's Encrypt certificate
#     DOMAIN=events.example.com  your own domain (default with HTTPS: <public-ip>.sslip.io)
#     SEED_DEMO_EVENTS=false     don't create the demo organizer's sample events (default: true)
#     FORCE=1                    deploy even on an instance with less than 1.8 GB of RAM
set -euo pipefail

K3S_VERSION="v1.31.4+k3s1"
CERT_MANAGER_VERSION="v1.16.2"
NS=eventhub
REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
CREDS_FILE="$HOME/eventhub-credentials.txt"
SEED_DEMO_EVENTS="${SEED_DEMO_EVENTS:-true}"

log()  { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
kc()   { sudo k3s kubectl "$@"; }

# ------------------------------------------------------------------ 1. machine checks
log "Checking the machine"
mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
echo "RAM: ${mem_mb} MB, CPUs: $(nproc), free disk on /: $(df -h / | awk 'NR==2 {print $4}')"
if [ "$mem_mb" -lt 1800 ] && [ "${FORCE:-0}" != 1 ]; then
  die "This needs at least 2 GB of RAM (e.g. t3.small). Use a bigger instance, or re-run with FORCE=1 to try anyway."
fi

if ! swapon --show | grep -q .; then
  log "Adding 2 GB of swap (helps the Java build on small instances)"
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# ------------------------------------------------------------------ 2. tools
if ! command -v docker >/dev/null 2>&1; then
  log "Installing Docker and git"
  sudo dnf install -y docker git
fi
sudo systemctl enable --now docker >/dev/null

if ! command -v k3s >/dev/null 2>&1; then
  log "Installing k3s ${K3S_VERSION}"
  curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="$K3S_VERSION" sh -s - --write-kubeconfig-mode 600
fi
log "Waiting for the Kubernetes node to be ready"
for _ in $(seq 1 60); do
  kc get nodes 2>/dev/null | grep -q ' Ready' && break
  sleep 5
done
kc get nodes

# ------------------------------------------------------------------ 3. address
TOKEN=$(curl -s -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60" || true)
PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4 || true)
[ -n "$PUBLIC_IP" ] || die "Could not read this instance's public IP. Does it have a public IPv4 address?"
if [ -n "${LE_EMAIL:-}" ]; then
  DOMAIN="${DOMAIN:-${PUBLIC_IP//./-}.sslip.io}"
  PUBLIC_URL="https://${DOMAIN}"
  COOKIE_SECURE=true
else
  PUBLIC_URL="http://${DOMAIN:-$PUBLIC_IP}"
  COOKIE_SECURE=false
fi
echo "Public URL will be: $PUBLIC_URL"

# ------------------------------------------------------------------ 4. images
log "Building container images (the backend build takes a few minutes the first time)"
sudo docker build -t eventhub-backend:local "$REPO_DIR/backend"
sudo docker build -t eventhub-frontend:local "$REPO_DIR/frontend"
log "Loading images into k3s"
sudo docker save eventhub-backend:local | sudo k3s ctr -n k8s.io images import -
sudo docker save eventhub-frontend:local | sudo k3s ctr -n k8s.io images import -
sudo docker image prune -f >/dev/null

# ------------------------------------------------------------------ 5. secrets and config
kc get namespace "$NS" >/dev/null 2>&1 || kc create namespace "$NS"

if ! kc -n "$NS" get secret eventhub-secrets >/dev/null 2>&1; then
  log "Generating secrets (first deploy only; kept on later deploys)"
  DB_PASSWORD=$(openssl rand -hex 24)
  JWT_SECRET=$(openssl rand -base64 32)
  ADMIN_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)
  ORGANIZER_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)
  kc -n "$NS" create secret generic eventhub-secrets \
    --from-literal=DB_PASSWORD="$DB_PASSWORD" \
    --from-literal=JWT_SECRET="$JWT_SECRET" \
    --from-literal=DEV_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    --from-literal=DEV_ORGANIZER_PASSWORD="$ORGANIZER_PASSWORD"
  umask 077
  cat > "$CREDS_FILE" <<EOF
EventHub login accounts (created on first start; change or remove as needed)
Admin:     admin@eventhub.local / ${ADMIN_PASSWORD}
Organizer: organizer@eventhub.local / ${ORGANIZER_PASSWORD}
EOF
  echo "Login details saved to $CREDS_FILE (readable only by you)."
fi

log "Writing app configuration"
kc -n "$NS" create configmap eventhub-config \
  --from-literal=DB_URL="jdbc:postgresql://postgres:5432/eventhub" \
  --from-literal=DB_USERNAME="eventhub" \
  --from-literal=JWT_ISSUER="eventhub" \
  --from-literal=CORS_ALLOWED_ORIGINS="$PUBLIC_URL" \
  --from-literal=FRONTEND_URL="$PUBLIC_URL" \
  --from-literal=REFRESH_COOKIE_SECURE="$COOKIE_SECURE" \
  --from-literal=REFRESH_COOKIE_SAME_SITE="Strict" \
  --from-literal=APP_TIMEZONE="Asia/Kolkata" \
  --from-literal=MAIL_ENABLED="false" \
  --from-literal=APP_SEED_ENABLED="true" \
  --from-literal=APP_SEED_DEMO_EVENTS="$SEED_DEMO_EVENTS" \
  --from-literal=DEV_ADMIN_EMAIL="admin@eventhub.local" \
  --from-literal=DEV_ORGANIZER_EMAIL="organizer@eventhub.local" \
  --dry-run=client -o yaml | kc apply -f -

# ------------------------------------------------------------------ 6. workloads
log "Applying Kubernetes manifests"
kc apply -k "$REPO_DIR/deploy/k8s"

if [ -n "${LE_EMAIL:-}" ]; then
  log "Enabling HTTPS for $DOMAIN (Let's Encrypt via cert-manager ${CERT_MANAGER_VERSION})"
  kc apply -f "https://github.com/cert-manager/cert-manager/releases/download/${CERT_MANAGER_VERSION}/cert-manager.yaml" >/dev/null
  kc -n cert-manager rollout status deploy/cert-manager-webhook --timeout=180s
  cat <<EOF | kc apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ${LE_EMAIL}
    privateKeySecretRef:
      name: letsencrypt-account
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
---
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: redirect-https
  namespace: ${NS}
spec:
  redirectScheme:
    scheme: https
    permanent: true
EOF
  kc -n "$NS" annotate ingress eventhub --overwrite \
    cert-manager.io/cluster-issuer=letsencrypt \
    traefik.ingress.kubernetes.io/router.middlewares="${NS}-redirect-https@kubernetescrd"
  kc -n "$NS" patch ingress eventhub --type=json -p "[
    {\"op\":\"add\",\"path\":\"/spec/tls\",\"value\":[{\"hosts\":[\"${DOMAIN}\"],\"secretName\":\"eventhub-tls\"}]},
    {\"op\":\"add\",\"path\":\"/spec/rules/0/host\",\"value\":\"${DOMAIN}\"}
  ]"
fi

log "Restarting pods with the new images"
kc -n "$NS" rollout restart deployment/backend deployment/frontend
kc -n "$NS" rollout status statefulset/postgres --timeout=180s
kc -n "$NS" rollout status deployment/frontend --timeout=180s
kc -n "$NS" rollout status deployment/backend --timeout=420s
kc -n "$NS" get pods -o wide

# ------------------------------------------------------------------ 7. smoke test
log "Smoke test"
HOST_HEADER=()
[ -n "${DOMAIN:-}" ] && HOST_HEADER=(-H "Host: ${DOMAIN}")
"$REPO_DIR/deploy/smoke-test.sh" "http://localhost" "${HOST_HEADER[@]}"

log "Done"
echo "EventHub is running at: $PUBLIC_URL"
[ -f "$CREDS_FILE" ] && echo "Login details: cat $CREDS_FILE"
if [ -n "${LE_EMAIL:-}" ]; then
  echo "HTTPS certificate status: sudo k3s kubectl -n $NS get certificate   (READY=True after ~1-2 minutes)"
fi
echo "Make sure the instance's security group allows inbound TCP 80$( [ -n "${LE_EMAIL:-}" ] && echo ' and 443') from the internet."

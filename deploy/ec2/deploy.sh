#!/usr/bin/env bash
# Deploys EventHub to a single EC2 instance running k3s (lightweight Kubernetes).
# Safe to re-run: it updates images and config and keeps existing secrets and data.
#
# Run from the repository root on the instance:
#     ./deploy/ec2/deploy.sh
# Options (environment variables):
#     LE_EMAIL=you@example.com   enable HTTPS with a free Let's Encrypt certificate
#     DOMAIN=events.example.com  your own domain (default with HTTPS: <public-ip>.sslip.io)
#     IMAGES=ghcr                use the images GitHub Actions publishes to ghcr.io instead of
#                                building on this machine (needed on 1 GB instances like t3.micro)
#     SEED_DEMO_EVENTS=false     don't create the demo organizer's sample events (default: true)
#     FORCE=1                    deploy even on an instance with less RAM than recommended
set -euo pipefail

K3S_VERSION="v1.31.4+k3s1"
CERT_MANAGER_VERSION="v1.16.2"
NS=eventhub
REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
CREDS_FILE="$HOME/eventhub-credentials.txt"
SEED_DEMO_EVENTS="${SEED_DEMO_EVENTS:-true}"
IMAGES="${IMAGES:-build}"
case "$IMAGES" in
  build) MANIFESTS="$REPO_DIR/deploy/k8s"; MIN_MEM_MB=1800 ;;
  ghcr)  MANIFESTS="$REPO_DIR/deploy/k8s-ghcr"; MIN_MEM_MB=850 ;;
  *) echo "IMAGES must be 'build' or 'ghcr'" >&2; exit 1 ;;
esac

log()  { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
kc()   { sudo k3s kubectl "$@"; }
# Manifests are validated in CI, so skip kubectl's client-side validation: it downloads the
# whole OpenAPI schema, which can time out on small, busy instances.
ka()   { kc apply --validate=false "$@"; }
# Wait until the Kubernetes API answers (it can be slow while pods start on small instances).
wait_api() {
  for _ in $(seq 1 60); do
    kc get --raw=/readyz >/dev/null 2>&1 && return 0
    echo "Kubernetes API is busy; waiting..."
    sleep 5
  done
  die "The Kubernetes API did not respond for 5 minutes. The instance is probably overloaded (check: uptime; free -h)."
}

# ------------------------------------------------------------------ 1. machine checks
log "Checking the machine"
mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
echo "RAM: ${mem_mb} MB, CPUs: $(nproc), free disk on /: $(df -h / | awk 'NR==2 {print $4}')"
if [ "$mem_mb" -lt "$MIN_MEM_MB" ] && [ "${FORCE:-0}" != 1 ]; then
  if [ "$IMAGES" = build ]; then
    die "Building images here needs at least 2 GB of RAM (e.g. t3.small). On a smaller instance use the prebuilt images: IMAGES=ghcr ./deploy/ec2/deploy.sh"
  fi
  die "This needs at least 1 GB of RAM. Re-run with FORCE=1 to try anyway."
fi
if [ "$IMAGES" = ghcr ] && [ "$mem_mb" -lt 1800 ]; then
  warn "Small instance (${mem_mb} MB): it will work for a demo, but expect slow start-up and little headroom."
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
if [ "$IMAGES" = build ]; then
  if ! command -v docker >/dev/null 2>&1; then
    log "Installing Docker and git"
    sudo dnf install -y docker git
  fi
  sudo systemctl enable --now docker >/dev/null
fi

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
if [ "$IMAGES" = build ]; then
  log "Building container images (the backend build takes a few minutes the first time)"
  sudo docker build -t eventhub-backend:local "$REPO_DIR/backend"
  sudo docker build -t eventhub-frontend:local "$REPO_DIR/frontend"
  log "Loading images into k3s"
  sudo docker save eventhub-backend:local | sudo k3s ctr -n k8s.io images import -
  sudo docker save eventhub-frontend:local | sudo k3s ctr -n k8s.io images import -
  sudo docker image prune -f >/dev/null
else
  log "Using prebuilt images from ghcr.io (built by GitHub Actions); Kubernetes will pull them"
fi

# ------------------------------------------------------------------ 5. secrets and config
wait_api
kc create namespace "$NS" --dry-run=client -o yaml | ka -f - >/dev/null

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
  --dry-run=client -o yaml | ka -f -

# ------------------------------------------------------------------ 6. workloads
log "Applying Kubernetes manifests"
for attempt in 1 2 3 4 5; do
  wait_api
  if ka -k "$MANIFESTS"; then break; fi
  [ "$attempt" = 5 ] && die "Could not apply the manifests. Re-run the script; if it keeps failing the instance is overloaded."
  echo "Apply failed; retrying in 10 s (attempt $attempt/5)..."
  sleep 10
done

if [ -n "${LE_EMAIL:-}" ]; then
  log "Enabling HTTPS for $DOMAIN (Let's Encrypt via cert-manager ${CERT_MANAGER_VERSION})"
  wait_api
  ka -f "https://github.com/cert-manager/cert-manager/releases/download/${CERT_MANAGER_VERSION}/cert-manager.yaml" >/dev/null
  for d in cert-manager cert-manager-cainjector cert-manager-webhook; do
    kc -n cert-manager rollout status "deploy/$d" --timeout=300s
  done
  ISSUER_FILE=$(mktemp)
  cat > "$ISSUER_FILE" <<EOF
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
  # The cert-manager webhook reports "rolled out" a little before it accepts requests,
  # so retry for up to ~3 minutes instead of failing on the first attempt.
  for attempt in $(seq 1 36); do
    if ka -f "$ISSUER_FILE" 2>/dev/null; then break; fi
    [ "$attempt" = 36 ] && { ka -f "$ISSUER_FILE"; die "cert-manager is not accepting requests; re-run the script in a minute."; }
    echo "cert-manager is still starting; retrying in 5 s (attempt $attempt/36)..."
    sleep 5
  done
  rm -f "$ISSUER_FILE"
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

#!/usr/bin/env bash
# Builds the Plant Shop images straight into minikube's Docker daemon, so the cluster
# can use them without a registry (imagePullPolicy: IfNotPresent).
#
#   ./scripts/build-images.sh            -> every service as 1.0.0, catalog-service also as 1.1.0
#   ./scripts/build-images.sh 1.2.0 catalog-service
set -euo pipefail
cd "$(dirname "$0")/.."

eval "$(minikube -p minikube docker-env --shell bash)"

build() {  # build <service> <version>
  echo ">>> plantshop/$1:$2"
  docker build --build-arg SERVICE="$1" --build-arg APP_VERSION="$2" -t "plantshop/$1:$2" .
}

if [ $# -ge 2 ]; then
  build "$2" "$1"
  exit 0
fi

for service in config-service discovery-service api-gateway catalog-service inventory-service order-service payment-service; do
  build "$service" 1.0.0
done
# Second version for the rolling update / rollback demo (same code, APP_VERSION=1.1.0)
build catalog-service 1.1.0

docker images "plantshop/*"

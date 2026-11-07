#!/usr/bin/env bash
# Runs SonarScanner CLI (pinned container image) against SonarQube and waits until the server has processed
# the report, so the Web API returns the fresh numbers right after this script.
#   docker compose -f tools/sonar/docker-compose.yml up -d     # local SonarQube Community on :9000
#   ./mvnw -DskipTests package                                 # sonar.java.binaries need compiled classes
#   SONAR_TOKEN=... ./scripts/sonar-scan.sh
# The token is read only from the environment and never written anywhere. SONAR_URL defaults to localhost.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

: "${SONAR_TOKEN:?export SONAR_TOKEN first (a SonarQube user token)}"
SONAR_URL=${SONAR_URL:-http://localhost:9000}
SCANNER=sonarsource/sonar-scanner-cli:12.2.0.4256_8.1.0
# inside the scanner container "localhost" is the container itself
container_url=${SONAR_URL/localhost/host.docker.internal}

ls ./*/target/classes > /dev/null 2>&1 || { echo "sonar-scan: compile first (./mvnw -DskipTests package)" >&2; exit 2; }

docker run --rm -e SONAR_HOST_URL="$container_url" -e SONAR_TOKEN \
  -v "$(pwd -W 2>/dev/null || pwd):/usr/src" "$SCANNER" -Dsonar.working.directory=.scannerwork

task_id=$(sed -n 's/^ceTaskId=//p' .scannerwork/report-task.txt)
echo "Waiting for background task $task_id ..."
for _ in $(seq 1 60); do
  status=$(curl -s -u "$SONAR_TOKEN:" "$SONAR_URL/api/ce/task?id=$task_id" | grep -o '"status":"[A-Z_]*"' | head -1)
  case "$status" in
    *SUCCESS*) echo "Analysis processed: $status"; exit 0 ;;
    *FAILED*|*CANCELED*) echo "Analysis failed: $status" >&2; exit 1 ;;
  esac
  sleep 2
done
echo "sonar-scan: timed out waiting for $task_id" >&2
exit 1

#!/usr/bin/env bash
# Privacy lab demonstration against the docker compose stack:
#   1 secure logging   2 personal data export   3 erasure / anonymization   4 consent enforcement
# Every command is printed with its unedited output.
#
#   ./scripts/privacy-demo.sh          reset the demo data, run steps 1-4, write docs/privacy-demo.md
#   ./scripts/privacy-demo.sh 1        run one step in the terminal (live defense); also: 2, 3, 4
#   ./scripts/privacy-demo.sh reset    restore the synthetic seed data of user-service
#
# Prerequisites: `docker compose up -d --build` with all containers running; JDK 21 for the test runs
# (JAVA_HOME, or a JetBrains-managed JDK in ~/.jdks).
set -uo pipefail
cd "$(dirname "$0")/.."

GW=localhost:8080
OUT=docs/privacy-demo.md
TS=$(date +%s)
# synthetic demo accounts from V2__seed_demo_users.sql
ALICE=alice@example.test:Alice-Demo-2026
BOB=bob@example.test:Bob-Demo-2026
DPO=dpo@example.test:Dpo-Demo-2026
MARKETING=marketing@example.test:Marketing-Demo-2026
ERIN=erin@example.test:Erin-Demo-2026

if [ -z "${JAVA_HOME:-}" ]; then
  for jdk in "$HOME"/.jdks/*21*; do [ -x "$jdk/bin/java" ] && export JAVA_HOME="$jdk" && break; done
fi

# --- helpers ---------------------------------------------------------------------------------
section() { printf '\n## %s\n' "$1"; }
note()    { printf '\n%s\n' "$1"; }
run() {   # run '<command>' - prints the command and its output; sed adds a missing final newline
  printf '\n```console\n$ %s\n' "$1"; eval "$1" 2>&1 | sed -e '$a\'; printf '```\n'
}
api() { curl -s -H 'Content-Type: application/json' -w '\nHTTP %{http_code}\n' "$@"; }
db()          { docker exec -e PGOPTIONS=--search_path=users    plantshop-user-db    psql -U plantshop -d user_db    -c "$1"; }
orders_db()   { docker exec -e PGOPTIONS=--search_path=orders   plantshop-order-db   psql -U plantshop -d order_db   -c "$1"; }
payments_db() { docker exec -e PGOPTIONS=--search_path=payments plantshop-payment-db psql -U plantshop -d payment_db -c "$1"; }
user_id() { docker exec plantshop-user-db psql -U plantshop -d user_db -tAc "select id from users.users where email = '$1'"; }
service_log() { docker logs plantshop-user-service 2>&1; }
mvn_test() {   # mvn_test <modules> <test classes> - runs the tests, prints the per-class summary
  ./mvnw -B -pl "$1" -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest="$2" 2>&1 \
    | grep -E 'Tests run:|BUILD (SUCCESS|FAILURE)|^\[ERROR\]   ' | sed 's/^\[[A-Z]*\] //'
}
order() {      # order <e-mail> <address> - places an order through the gateway and waits for the saga
  local id
  id=$(curl -s -H 'Content-Type: application/json' -X POST "$GW/api/orders" \
        -d "{\"customerEmail\":\"$1\",\"shippingAddress\":\"$2\",\"paymentMethod\":\"CARD\",\"items\":[{\"plantId\":1,\"quantity\":1}]}" \
        | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  for _ in $(seq 20); do
    curl -s "$GW/api/orders/$id" | grep -qE '"status":"(CONFIRMED|CANCELLED)"' && break
    sleep 1
  done
  curl -s "$GW/api/orders/$id" | grep -oE '"(id|status|totalAmount)":("[^"]*"|[0-9.]+)' | head -3 | paste -sd' '
}
wait_for_user_service() {
  for _ in $(seq 60); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/users/1/personal-data")" = 401 ] && return 0
    sleep 3
  done
  echo "user-service is not reachable through the gateway at $GW" >&2; exit 1
}

# --- steps -----------------------------------------------------------------------------------
step_reset() {
  section "0. Demo data"
  note "The user-service tables are emptied and the synthetic seed is loaded again (the same SQL Flyway ran)."
  run "{ echo 'TRUNCATE users, user_addresses, user_settings, user_activity, user_consents, consent_history, email_outbox, analytics_events, privacy_audit_events RESTART IDENTITY CASCADE;'; cat user-service/src/main/resources/db/migration/V2__seed_demo_users.sql; } | docker exec -i -e PGOPTIONS=--search_path=users plantshop-user-db psql -q -v ON_ERROR_STOP=1 -U plantshop -d user_db && echo 'seed data restored'"
  run "db 'select id, email, full_name, role, status from users order by id'"
}

step1() {
  local cid="lab-logging-$TS" email="log.demo.$TS@example.test" password="Demo-Passw0rd-$TS"
  section "1. Secure logging"
  note "Registration processes an e-mail, a phone number and a password and creates a one-time verification token; \
a password reset creates a reset token. The mail-provider call is logged at DEBUG with the provider API key in the \
Authorization header and the tokens in the JSON body. Every line passes through MaskingEncoder (secure-logging)."
  run "api -X POST $GW/api/users -H 'X-Correlation-Id: $cid' -d '{\"fullName\":\"Log Demo\",\"email\":\"$email\",\"phone\":\"+44 7700 900999\",\"password\":\"$password\"}'"
  run "api -X POST $GW/api/users/password-reset -H 'X-Correlation-Id: $cid' -d '{\"email\":\"$email\"}'"
  note "The log lines of both requests, found by their correlation id: identifiers partially masked, secrets \
redacted, the security events carry only pseudonyms."
  run "service_log | grep '$cid'"
  note "The original values occur nowhere in the service log (count of matching lines):"
  run "for v in '$email' '+44 7700 900999' '+447700900999' '$password' 'demo-provider-api-key-not-real'; do printf '%-36s %s\n' \"\$v\" \"\$(service_log | grep -cF -- \"\$v\")\"; done"
  note "Automated tests: masking rules (text, JSON, escaped JSON, nested DTO, exception chain, control run without \
masking), the shipped logback configuration (text and JSON profile), and the real service operations."
  run "mvn_test secure-logging,user-service 'PiiMaskerTest,MaskingEncoderTest,SecureLoggingConfigurationTest,SecureLoggingIntegrationTest'"
}

step2() {
  local alice bob
  alice=$(user_id alice@example.test); bob=$(user_id bob@example.test)
  section "2. Personal data export (right of access)"
  note "Preparation: Alice places an order, so the export also contains data held by order-service."
  run "order alice@example.test '1 Fern Street, Kyiv'"
  note "Alice exports her own data. The authenticated principal is compared with the subject id in the URL. \
Every section names its source in \`sources\`; password hash, tokens and the antifraud flag are listed in \
\`excludedFields\` but never exported."
  run "api -u $ALICE $GW/api/users/$alice/personal-data"
  note "Negative scenario: Bob asks for Alice's export, then for an id that does not exist. Both answers are the \
same 404 without data, so Bob cannot tell that profile $alice exists."
  run "api -u $BOB $GW/api/users/$alice/personal-data"
  run "api -u $BOB $GW/api/users/999999/personal-data"
  run "api $GW/api/users/$alice/personal-data"
  note "What the service logged: the fact, the result and pseudonymous ids, never the exported data."
  run "service_log | grep -E 'Personal data export generated|PERSONAL_DATA_EXPORT' | tail -4"
  run "mvn_test user-service,order-service 'PersonalDataExportIntegrationTest,CustomerDataIntegrationTest'"
}

step3() {
  local erin alice
  erin=$(user_id erin@example.test); alice=$(user_id alice@example.test)
  section "3. Erasure / anonymization"
  note "Preparation: Erin places two orders (order-service), which are paid (payment-service)."
  run "order erin@example.test '5 Cactus Lane, Odesa'"
  run "order erin@example.test '5 Cactus Lane, Odesa'"
  note "BEFORE: the records linked to Erin in every store."
  run "db \"select id, email, full_name, phone, external_subject_id, password_hash is not null as has_password, status from users where id = $erin\""
  run "db \"select (select count(*) from user_addresses where user_id = $erin) addresses, (select count(*) from user_settings where user_id = $erin) settings, (select count(*) from user_activity where user_id = $erin) activity, (select count(*) from email_outbox where user_id = $erin) emails, (select count(*) from analytics_events where user_id = $erin) analytics, (select count(*) from user_consents where user_id = $erin and status = 'GRANTED') granted_consents\""
  run "db \"select activity_type, details, ip_address, occurred_at from user_activity where user_id = $erin\""
  run "orders_db \"select o.id, o.customer_email, o.shipping_address, o.status, o.total_amount, count(i.id) items from orders o join order_items i on i.order_id = o.id where o.customer_email = 'erin@example.test' group by o.id order by o.id\""
  local order_ids
  order_ids=$(docker exec plantshop-order-db psql -U plantshop -d order_db -tAc "select string_agg(id::text, ',') from orders.orders where customer_email = 'erin@example.test'")
  run "payments_db 'select order_id, amount, method, status from payments where order_id in ($order_ids) order by order_id'"
  note "The erasure command: POST (never GET), by Erin herself. The response lists the rule applied to every \
store (DELETE / ANONYMIZE / RETAIN / SKIP) with the number of records."
  run "api -u $ERIN -X POST $GW/api/users/$erin/anonymize"
  note "AFTER: the same queries."
  run "db \"select id, email, full_name, phone, external_subject_id, password_hash is not null as has_password, status, anonymized_at from users where id = $erin\""
  run "db \"select (select count(*) from user_addresses where user_id = $erin) addresses, (select count(*) from user_settings where user_id = $erin) settings, (select count(*) from user_activity where user_id = $erin) activity, (select count(*) from email_outbox where user_id = $erin) emails, (select count(*) from analytics_events where user_id = $erin) analytics, (select count(*) from user_consents where user_id = $erin and status = 'GRANTED') granted_consents\""
  run "db \"select purpose, status, source, withdrawn_at from user_consents where user_id = $erin\""
  run "db \"select user_id, event_name, page, occurred_at from analytics_events where user_id is null\""
  run "orders_db \"select o.id, o.customer_email, o.shipping_address, o.status, o.total_amount, count(i.id) items from orders o join order_items i on i.order_id = o.id where o.id in ($order_ids) group by o.id order by o.id\""
  run "payments_db 'select order_id, amount, method, status from payments where order_id in ($order_ids) order by order_id'"
  note "No trace of Erin's identifiers in any table of user-service (rows containing them):"
  run "db \"select count(*) from (select row_to_json(t)::text r from users t union all select row_to_json(t)::text from user_addresses t union all select row_to_json(t)::text from user_activity t union all select row_to_json(t)::text from email_outbox t union all select row_to_json(t)::text from privacy_audit_events t union all select row_to_json(t)::text from consent_history t) x where r ilike '%erin%' or r like '%+447700900505%' or r like '%203.0.113.50%' or r like '%google-demo-1005%'\""
  note "Repeated request (by the privacy officer, Erin can no longer log in): nothing changes."
  run "api -u $DPO -X POST $GW/api/users/$erin/anonymize | grep -E '\"status\"|HTTP'"
  run "api -u $ERIN $GW/api/users/$erin/personal-data"
  note "Privacy-safe audit trail: fact, time, result, pseudonymous actor/subject and correlation id."
  run "db \"select event_type, actor_ref, subject_ref, outcome, correlation_id, occurred_at from privacy_audit_events where event_type = 'ERASURE' order by id\""
  note "Another customer cannot erase Alice (same 404 as for a missing profile), and a GET never erases."
  run "api -u $BOB -X POST $GW/api/users/$alice/anonymize"
  run "api -u $ALICE $GW/api/users/$alice/anonymize | grep -E '\"status\"|HTTP'"
  run "mvn_test user-service,order-service 'ErasureIntegrationTest,CustomerDataIntegrationTest'"
}

step4() {
  local alice
  alice=$(user_id alice@example.test)
  local send="api -u $MARKETING -X POST $GW/api/marketing/emails -d '{\"userId\":$alice,\"campaign\":\"autumn-sale\"}'"
  local count="db \"select count(*) as marketing_emails_to_alice from email_outbox where user_id = $alice and category = 'MARKETING'\""
  section "4. Consent policy enforcement"
  note "Alice's consents: OPTIONAL_ANALYTICS granted, MARKETING_EMAIL not (yet) given."
  run "api -u $ALICE $GW/api/users/$alice/consents"
  note "DENY: the marketing operator sends the campaign. The Policy Gate checks the current consent right before \
the send; analytics consent does not count for marketing."
  run "$send"
  run "$count"
  note "GRANT for exactly this purpose and the current policy version, through the WEB channel."
  run "api -u $ALICE -X POST $GW/api/users/$alice/consents/MARKETING_EMAIL/grant -d '{\"policyVersion\":\"marketing-v2\",\"source\":\"WEB\"}'"
  note "ALLOW: the same send now goes out; the side effect is a row in email_outbox."
  run "$send"
  run "$count"
  note "REVOKE."
  run "api -u $ALICE -X POST $GW/api/users/$alice/consents/MARKETING_EMAIL/revoke -d '{\"source\":\"WEB\"}'"
  note "DENY again, immediately, and without a side effect."
  run "$send"
  run "$count"
  note "Evidence: the history of the consent (policy version, channel, time, correlation id)."
  run "db \"select purpose, action, policy_version, source, correlation_id, occurred_at from consent_history where user_id = $alice order by id\""
  note "Integration test of the scenario GRANT -> ALLOW -> REVOKE -> DENY, purpose isolation, policy version, \
idempotency, contractual e-mail not gated."
  run "mvn_test user-service ConsentIntegrationTest"
}

header() {
  cat <<EOF
# Privacy controls: demo transcript

Generated by \`scripts/privacy-demo.sh\` on $(date '+%Y-%m-%d %H:%M'). Every block is a command that was actually
run and its unedited output; all data is synthetic. Requests go through the API gateway (\`$GW\`).
\`api\` is \`curl -s -H 'Content-Type: application/json'\` printing the HTTP status; \`db\`, \`orders_db\` and
\`payments_db\` run psql in the database container of user-, order- and payment-service. The analysis and
design decisions are in [privacy-report.md](privacy-report.md).
EOF
}

wait_for_user_service
case "${1:-all}" in
  reset) step_reset ;;
  1) step1 ;;
  2) step2 ;;
  3) step3 ;;
  4) step4 ;;
  all) mkdir -p docs; { header; step_reset; step1; step2; step3; step4; } | tee "$OUT" ;;
  *) echo "usage: $0 [reset|1|2|3|4]" >&2; exit 1 ;;
esac

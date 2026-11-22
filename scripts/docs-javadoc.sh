#!/usr/bin/env bash
# Lab 11 (elective item for API projects): static reference generated from the Javadoc comments of the code.
# Output: target/reports/apidocs/index.html; CI copies it to site/javadoc/ before publishing.
set -uo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -q -DskipTests -Ddoclint=none -Dmaven.javadoc.failOnWarnings=false \
  -Dnotimestamp=true javadoc:aggregate "$@"

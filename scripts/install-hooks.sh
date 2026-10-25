#!/usr/bin/env bash
# Installs the versioned git hooks of this repository into .git/hooks/ (git does not version that directory).
#   ./scripts/install-hooks.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

hooks_dir=$(git rev-parse --git-path hooks)
for hook in scripts/git-hooks/*; do
  name=$(basename "$hook")
  cp "$hook" "$hooks_dir/$name"
  chmod +x "$hooks_dir/$name"
  echo "installed $hooks_dir/$name"
done

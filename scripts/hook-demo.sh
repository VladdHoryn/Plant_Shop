#!/usr/bin/env bash
# Live demo of the pre-commit hook (FAIL -> PASS) on a throwaway branch, so it can be repeated at will:
#   1. a function with CC = 12 (nested if/else) is staged and "git commit" is attempted -> the hook blocks it
#   2. the same function rewritten as a lookup table (CC = 1) is staged and committed -> accepted
#   3. back to the original branch; the demo branch is deleted
# Requires a clean working tree and the installed hook (./scripts/install-hooks.sh).
#   ./scripts/hook-demo.sh            (pauses before each commit; NO_PAUSE=1 to run straight through)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

FILE=catalog-service/src/main/java/com/plantshop/catalog/service/ShippingPlanner.java
ORIGINAL=$(git rev-parse --abbrev-ref HEAD)
DEMO=hook-demo-$(date +%s)
pause() { [ -n "${NO_PAUSE:-}" ] || read -rp $'\n[Enter] ' _ < /dev/tty; }
show() { printf '\n\e[1m$ %s\e[0m\n' "$*"; eval "$*"; }

[ -x "$(git rev-parse --git-path hooks)/pre-commit" ] || { echo "hook not installed: ./scripts/install-hooks.sh"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "working tree not clean, commit or stash first"; exit 1; }
git switch -q -c "$DEMO"
trap 'git switch -q -f "$ORIGINAL"; git branch -q -D "$DEMO"; echo; echo "demo branch $DEMO removed, back on $ORIGINAL"' EXIT

cat > "$FILE" <<'JAVA'
package com.plantshop.catalog.service;

import com.plantshop.catalog.entity.CareLevel;
import org.springframework.stereotype.Component;

/** Delivery days for a plant: depends on how fragile it is and on the region. */
@Component
public class ShippingPlanner {

    public int deliveryDays(CareLevel care, String region) {
        if (region.equals("KYIV")) {
            if (care == CareLevel.EASY) {
                return 1;
            } else if (care == CareLevel.MEDIUM) {
                return 2;
            } else {
                return 2;
            }
        } else if (region.equals("WEST")) {
            if (care == CareLevel.EASY) {
                return 2;
            } else if (care == CareLevel.MEDIUM) {
                return 3;
            } else {
                return 4;
            }
        } else if (region.equals("EAST") || region.equals("SOUTH")) {
            if (care == CareLevel.EASY) {
                return 3;
            } else if (care == CareLevel.MEDIUM) {
                return 4;
            } else {
                return 5;
            }
        }
        return care == CareLevel.HARD ? 7 : 5;
    }
}
JAVA
echo "=== 1. Messy function (nested conditions) ==="
show "git add $FILE"
pause
show 'git commit -m "feat: add messy code"'
show 'git log --oneline -1'

cat > "$FILE" <<'JAVA'
package com.plantshop.catalog.service;

import com.plantshop.catalog.entity.CareLevel;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Delivery days for a plant: depends on how fragile it is and on the region. */
@Component
public class ShippingPlanner {

    /** region -> [EASY, MEDIUM, HARD] delivery days; regions without an entry use OTHER. */
    private static final Map<String, int[]> DAYS = Map.of(
            "KYIV", new int[]{1, 2, 2},
            "WEST", new int[]{2, 3, 4},
            "EAST", new int[]{3, 4, 5},
            "SOUTH", new int[]{3, 4, 5},
            "OTHER", new int[]{5, 5, 7});

    public int deliveryDays(CareLevel care, String region) {
        return DAYS.getOrDefault(region, DAYS.get("OTHER"))[care.ordinal()];
    }
}
JAVA
echo
echo "=== 2. The same rules as a lookup table ==="
show "git add $FILE"
pause
show 'git commit -m "feat(catalog): delivery days as a lookup table"'
show 'git log --oneline -2'

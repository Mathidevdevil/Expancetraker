#!/bin/sh
# Spendwise pre-commit secret guard
set -e

if command -v gitleaks >/dev/null 2>&1; then
  gitleaks protect --staged --verbose --config=.gitleaks.toml
  exit $?
fi

# Built-in fallback secret scanner when gitleaks binary is not installed globally
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -Ev '^(web/package-lock\.json|\.env\.example|\.gitleaks\.toml|scripts/)' || true)
[ -z "$STAGED_FILES" ] && exit 0

BLOCKED_FILES=$(printf '%s\n' "$STAGED_FILES" | grep -E '(^\.env$|^web/\.env|keystore\.properties$|\.jks$|\.keystore$|local\.properties$)' || true)
if [ -n "$BLOCKED_FILES" ]; then
  echo "ERROR: Refusing to commit sensitive environment or keystore files:"
  echo "$BLOCKED_FILES"
  exit 1
fi

for f in $STAGED_FILES; do
  [ -f "$f" ] || continue
  if git diff --cached -- "$f" | grep -E '^\+' | grep -qE '(sb_secret_|AIza[0-9A-Za-z_-]{35}|service_role.*eyJ|(storePassword|keyPassword)\s*=\s*["'\''][^"'\'']+["'\'']|postgresql://postgres\.[^:]+:[^\[][^@]+@)'; then
    echo "ERROR: Potential secret detected in staged changes for $f"
    echo "Remove hardcoded credentials and use environment variables or local.properties/keystore.properties."
    exit 1
  fi
done

exit 0

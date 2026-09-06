#!/usr/bin/env bash
# Fails if a call site uses the tagless `log { }` form, whose tag would have to
# come from a runtime class name that R8 renames. Run from the repository root.
set -euo pipefail

hits=$(grep -rnE '(^|[^a-zA-Z._`])log(\((INFO|WARN|ERROR|VERBOSE|DEBUG)\))? \{' --include='*.kt' \
    app/src app-common/src app-common-test/src module-core/src modules-*/src sync-core/src syncs-*/src \
    | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(\*|//)' || true)

if [ -n "$hits" ]; then
    echo "Tagless log { } call sites found:"
    echo "$hits"
    exit 1
fi
echo "OK: every log call passes an explicit tag."

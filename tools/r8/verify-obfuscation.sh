#!/usr/bin/env bash
# Verifies that the gplay build is obfuscated and the foss build is not.
#
# Run from the repository root after:
#   ./gradlew assembleGplayRelease assembleFossRelease bundleGplayRelease
#
# mapping.txt is ~87 MB, so every check streams it via grep/awk instead of
# reading it into a variable.
set -euo pipefail

GPLAY_MAPPING_DIR="app/build/outputs/mapping/gplayRelease"
FOSS_MAPPING_DIR="app/build/outputs/mapping/fossRelease"
BUNDLE_GLOB="app/build/outputs/bundle/gplayRelease"

MIN_RENAMED=500

failures=0

pass() {
    echo "PASS: $1"
}

fail() {
    echo "FAIL: $1"
    failures=$((failures + 1))
}

require_file() {
    if [ ! -f "$1" ]; then
        fail "missing $1 (did you run the assemble/bundle tasks?)"
        return 1
    fi
    return 0
}

check_dontobfuscate() {
    local gplay_config="$GPLAY_MAPPING_DIR/configuration.txt"
    local foss_config="$FOSS_MAPPING_DIR/configuration.txt"

    require_file "$gplay_config" || return 0
    require_file "$foss_config" || return 0

    if grep -qE '^-dontobfuscate' "$gplay_config"; then
        fail "gplayRelease configuration.txt contains -dontobfuscate"
    else
        pass "gplayRelease configuration.txt has no -dontobfuscate"
    fi

    if grep -qE '^-dontobfuscate' "$foss_config"; then
        pass "fossRelease configuration.txt contains -dontobfuscate"
    else
        fail "fossRelease configuration.txt is missing -dontobfuscate"
    fi
}

check_line_numbers() {
    local gplay_config="$GPLAY_MAPPING_DIR/configuration.txt"

    require_file "$gplay_config" || return 0

    local match
    match=$(grep -m1 -E '^-keepattributes.*LineNumberTable' "$gplay_config" || true)
    if [ -n "$match" ]; then
        pass "gplayRelease keeps line numbers ($match)"
    else
        fail "gplayRelease configuration.txt has no -keepattributes ... LineNumberTable"
    fi
}

# Prints "<renamed> <total>" for the given mapping file.
count_renames() {
    { grep -E '^eu\.darken\.octi\..* -> ' "$1" || true; } \
        | { grep -v 'R8\$\$REMOVED' || true; } \
        | awk -F' -> ' '{ total++; if ($1 ":" != $2) renamed++ } END { print renamed + 0, total + 0 }'
}

check_renames() {
    local gplay_mapping="$GPLAY_MAPPING_DIR/mapping.txt"
    local foss_mapping="$FOSS_MAPPING_DIR/mapping.txt"

    require_file "$gplay_mapping" || return 0
    require_file "$foss_mapping" || return 0

    local gplay_renamed gplay_total gplay_pct
    read -r gplay_renamed gplay_total < <(count_renames "$gplay_mapping")
    if [ "$gplay_total" -eq 0 ]; then
        fail "gplayRelease mapping.txt lists no eu.darken.octi classes"
    else
        gplay_pct=$((gplay_renamed * 100 / gplay_total))
        if [ "$gplay_renamed" -ge "$MIN_RENAMED" ]; then
            pass "gplayRelease renamed $gplay_renamed/$gplay_total ($gplay_pct%)"
        else
            fail "gplayRelease renamed $gplay_renamed/$gplay_total ($gplay_pct%), expected >= $MIN_RENAMED"
        fi
    fi

    local foss_renamed foss_total foss_pct
    read -r foss_renamed foss_total < <(count_renames "$foss_mapping")
    foss_pct=0
    if [ "$foss_total" -gt 0 ]; then
        foss_pct=$((foss_renamed * 100 / foss_total))
    fi
    if [ "$foss_renamed" -eq 0 ]; then
        pass "fossRelease renamed $foss_renamed/$foss_total ($foss_pct%)"
    else
        fail "fossRelease renamed $foss_renamed/$foss_total ($foss_pct%), expected 0"
    fi
}

check_keep_targets() {
    local gplay_mapping="$GPLAY_MAPPING_DIR/mapping.txt"

    require_file "$gplay_mapping" || return 0

    local keep_targets=(
        "eu.darken.octi.BuildConfig"
        "eu.darken.octi.main.ui.MainActivity"
        "eu.darken.octi.sync.core.worker.SyncWorker"
        "eu.darken.octi.main.ui.dashboard.DashboardVM"
        "eu.darken.octi.main.ui.MainActivityVM"
        "eu.darken.octi.modules.clipboard.ui.widget.CopyClipboardCallback"
        "eu.darken.octi.modules.connectivity.ui.widget.CopyNetworkAddressesCallback"
        "eu.darken.octi.syncs.octiserver.core.InvalidLinkCodeException"
        "eu.darken.octi.modules.power.core.alert.PowerAlertNotificationReceiver"
    )

    local cls
    for cls in "${keep_targets[@]}"; do
        if grep -qF "$cls -> $cls:" "$gplay_mapping"; then
            pass "kept name: $cls"
        else
            fail "not kept (or absent): $cls"
        fi
    done
}

check_bundle_mapping() {
    local aab
    aab=$(ls "$BUNDLE_GLOB"/*.aab 2>/dev/null | head -1 || true)
    if [ -z "$aab" ]; then
        fail "no .aab in $BUNDLE_GLOB (did you run bundleGplayRelease?)"
        return 0
    fi

    if unzip -l "$aab" | grep -F "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map" > /dev/null; then
        pass "$aab embeds proguard.map"
    else
        fail "$aab does not embed BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
    fi
}

check_dontobfuscate
check_line_numbers
check_renames
check_keep_targets
check_bundle_mapping

echo
if [ "$failures" -eq 0 ]; then
    echo "All obfuscation checks passed."
else
    echo "$failures obfuscation check(s) failed."
    exit 1
fi

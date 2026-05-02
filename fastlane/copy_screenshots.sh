#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

REFERENCE_DIR="$PROJECT_DIR/app/src/screenshotTestGplayDebug/reference/eu/darken/octi/screenshots/PlayStoreScreenshotsKt"
FASTLANE_DIR="$PROJECT_DIR/fastlane/metadata/android"

usage() {
    echo "Usage: $0 [--locale FASTLANE_LOCALE] [--clean] [--dry-run]"
    echo ""
    echo "Options:"
    echo "  --locale NAME  Copy one fastlane locale, e.g. en-US. Can be passed more than once"
    echo "  --clean    Remove existing phoneScreenshots before copying"
    echo "  --dry-run  Show copy operations without writing files"
    echo "  --help     Show this help"
    exit 0
}

CLEAN=false
DRY_RUN=false
REQUESTED_LOCALES=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --locale) REQUESTED_LOCALES+=("$2"); shift 2 ;;
        --clean) CLEAN=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        --help) usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

locale_requested() {
    local locale="$1"
    if [[ ${#REQUESTED_LOCALES[@]} -eq 0 ]]; then
        return 0
    fi
    for requested in "${REQUESTED_LOCALES[@]}"; do
        [[ "$locale" == "$requested" ]] && return 0
    done
    return 1
}

unsupported_locale() {
    case "$1" in
        ckb-IR|es-AR|sc-IT|sq-AL|uz|pcm-NG|tl-PH|ku-TR|kmr-TR|ur-IN|zu|si-LK|pa-IN) return 0 ;;
        *) return 1 ;;
    esac
}

for locale_name in "${REQUESTED_LOCALES[@]}"; do
    if [[ ! -d "$FASTLANE_DIR/$locale_name" ]] || unsupported_locale "$locale_name"; then
        echo "ERROR: Unsupported or missing fastlane locale: $locale_name"
        exit 1
    fi
done

declare -A SCREEN_MAP=(
    [DashboardLight]="01_dashboard_light"
    [DashboardDark]="02_dashboard_dark"
    [FileSharing]="03_file_sharing"
    [Apps]="04_apps"
    [SyncServices]="05_sync_services"
    [Widgets]="06_widgets"
)

if [[ ! -d "$REFERENCE_DIR" ]]; then
    echo "ERROR: Reference directory not found: $REFERENCE_DIR"
    echo "Run ./fastlane/generate_screenshots.sh first."
    exit 1
fi

if [[ "$CLEAN" == true && "$DRY_RUN" == false ]]; then
    echo "Cleaning existing phoneScreenshots directories..."
    if [[ ${#REQUESTED_LOCALES[@]} -gt 0 ]]; then
        for locale_name in "${REQUESTED_LOCALES[@]}"; do
            rm -rf "$FASTLANE_DIR/$locale_name/images/phoneScreenshots"
        done
    else
        find "$FASTLANE_DIR" -path "*/images/phoneScreenshots" -type d -exec rm -rf {} + 2>/dev/null || true
    fi
fi

COPIED=0
SKIPPED=0

for png_file in "$REFERENCE_DIR"/*.png; do
    [[ -f "$png_file" ]] || continue

    filename=$(basename "$png_file" .png)
    screen_name=""
    remainder=""

    for func_name in "${!SCREEN_MAP[@]}"; do
        if [[ "$filename" == "${func_name}_"* ]]; then
            screen_name="$func_name"
            remainder="${filename#${func_name}_}"
            break
        fi
    done

    if [[ -z "$screen_name" || -z "$remainder" ]]; then
        echo "SKIP: Cannot parse filename: $filename.png"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    temp="${remainder%_*}"
    locale_name="${temp%_*}"

    if [[ -z "$locale_name" ]]; then
        echo "SKIP: Cannot extract locale from: $filename.png"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    if unsupported_locale "$locale_name"; then
        echo "SKIP: Unsupported Play locale: $locale_name"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    if ! locale_requested "$locale_name"; then
        continue
    fi

    if [[ ! -d "$FASTLANE_DIR/$locale_name" ]]; then
        echo "SKIP: No fastlane metadata directory for locale: $locale_name"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    screenshot_name="${SCREEN_MAP[$screen_name]}"
    target_dir="$FASTLANE_DIR/$locale_name/images/phoneScreenshots"

    if [[ "$DRY_RUN" == true ]]; then
        echo "COPY: $filename.png -> $locale_name/images/phoneScreenshots/$screenshot_name.png"
    else
        mkdir -p "$target_dir"
        cp "$png_file" "$target_dir/$screenshot_name.png"
    fi
    COPIED=$((COPIED + 1))
done

echo ""
echo "Copy complete. Copied: $COPIED, skipped: $SKIPPED"

if (( SKIPPED > 0 )); then
    echo "ERROR: $SKIPPED reference image(s) could not be copied."
    exit 1
fi

if [[ "$DRY_RUN" == true ]]; then
    echo "Dry run only; no files were copied."
    exit 0
fi

echo ""
echo "Validation:"
INCOMPLETE=0
while IFS= read -r locale_dir; do
    locale_name=$(basename "$locale_dir")
    unsupported_locale "$locale_name" && continue
    locale_requested "$locale_name" || continue

    screenshot_dir="$locale_dir/images/phoneScreenshots"
    count=0
    if [[ -d "$screenshot_dir" ]]; then
        count=$(find "$screenshot_dir" -name "*.png" | wc -l)
    fi

    expected=${#SCREEN_MAP[@]}
    if (( count != expected )); then
        echo "  INCOMPLETE: $locale_name has $count/$expected screenshots"
        INCOMPLETE=$((INCOMPLETE + 1))
    fi
done < <(find "$FASTLANE_DIR" -maxdepth 1 -mindepth 1 -type d | sort)

if (( INCOMPLETE == 0 )); then
    echo "  All supported locales have complete screenshot sets."
else
    echo "  $INCOMPLETE locale(s) have incomplete screenshot sets."
    exit 1
fi

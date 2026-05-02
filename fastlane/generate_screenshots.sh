#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

LOCALES_FILE="$PROJECT_DIR/app/src/screenshotTest/kotlin/eu/darken/octi/screenshots/PlayStoreLocales.kt"
REFERENCE_DIR="$PROJECT_DIR/app/src/screenshotTestGplayDebug/reference"
FASTLANE_DIR="$PROJECT_DIR/fastlane/metadata/android"
GRADLE="$PROJECT_DIR/gradlew"
GRADLE_TASK="updateGplayDebugScreenshotTest"
BATCH_SIZE=2

usage() {
    echo "Usage: $0 [--smoke] [--locale FASTLANE_LOCALE] [--batch-size N] [--clean]"
    echo ""
    echo "Options:"
    echo "  --smoke        Use 6 representative locales for fast iteration"
    echo "  --locale NAME  Generate one fastlane locale, e.g. en-US. Can be passed more than once"
    echo "  --batch-size N Number of locales per Gradle invocation (default: $BATCH_SIZE)"
    echo "  --clean        Remove existing reference images before generating"
    echo "  --help         Show this help"
    exit 0
}

SMOKE=false
CLEAN=false
REQUESTED_LOCALES=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --smoke) SMOKE=true; shift ;;
        --locale) REQUESTED_LOCALES+=("$2"); shift 2 ;;
        --batch-size) BATCH_SIZE="$2"; shift 2 ;;
        --clean) CLEAN=true; shift ;;
        --help) usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

if [[ "$SMOKE" == true && ${#REQUESTED_LOCALES[@]} -gt 0 ]]; then
    echo "ERROR: --smoke and --locale cannot be combined"
    exit 1
fi

if [[ ! "$BATCH_SIZE" =~ ^[0-9]+$ ]] || (( BATCH_SIZE < 1 )); then
    echo "ERROR: --batch-size must be a positive integer"
    exit 1
fi

unsupported_locale() {
    case "$1" in
        ckb-IR|es-AR|sc-IT|sq-AL|uz|pcm-NG|tl-PH|ku-TR|kmr-TR|ur-IN|zu|si-LK|pa-IN) return 0 ;;
        *) return 1 ;;
    esac
}

android_locale_for() {
    case "$1" in
        en-US) echo "en" ;;
        af) echo "af-rZA" ;;
        cs-CZ) echo "cs" ;;
        da-DK) echo "da" ;;
        de-DE) echo "de" ;;
        el-GR) echo "el" ;;
        es-ES) echo "es" ;;
        fi-FI) echo "fi" ;;
        fr-FR) echo "fr" ;;
        hu-HU) echo "hu" ;;
        it-IT) echo "it" ;;
        iw-IL) echo "iw" ;;
        ja-JP) echo "ja" ;;
        ko-KR) echo "ko" ;;
        nl-NL) echo "nl" ;;
        no-NO) echo "nb" ;;
        pl-PL) echo "pl" ;;
        pt-BR) echo "pt-rBR" ;;
        pt-PT) echo "pt" ;;
        ru-RU) echo "ru" ;;
        sv-SE) echo "sv" ;;
        tr-TR) echo "tr" ;;
        zh-CN) echo "zh-rCN" ;;
        zh-TW) echo "zh-rTW" ;;
        *) echo "$1" ;;
    esac
}

locale_entry_for() {
    local fastlane_locale="$1"
    if [[ ! -d "$FASTLANE_DIR/$fastlane_locale" ]] || unsupported_locale "$fastlane_locale"; then
        return 1
    fi
    echo "$fastlane_locale:$(android_locale_for "$fastlane_locale")"
}

LOCALES=()
if [[ "$SMOKE" == true ]]; then
    for fastlane_locale in en-US de-DE ja-JP ar pt-BR zh-CN; do
        entry="$(locale_entry_for "$fastlane_locale" || true)"
        [[ -n "$entry" ]] && LOCALES+=("$entry")
    done
elif [[ ${#REQUESTED_LOCALES[@]} -gt 0 ]]; then
    for fastlane_locale in "${REQUESTED_LOCALES[@]}"; do
        entry="$(locale_entry_for "$fastlane_locale" || true)"
        if [[ -z "$entry" ]]; then
            echo "ERROR: Unsupported or missing fastlane locale: $fastlane_locale"
            exit 1
        fi
        LOCALES+=("$entry")
    done
else
    while IFS= read -r fastlane_locale; do
        entry="$(locale_entry_for "$fastlane_locale" || true)"
        [[ -n "$entry" ]] && LOCALES+=("$entry")
    done < <(find "$FASTLANE_DIR" -maxdepth 1 -mindepth 1 -type d -printf "%f\n" | sort)
fi

NUM_LOCALES=${#LOCALES[@]}
if (( NUM_LOCALES == 0 )); then
    echo "ERROR: No supported fastlane locales found in $FASTLANE_DIR"
    exit 1
fi

if [[ "$CLEAN" == true && -d "$REFERENCE_DIR" ]]; then
    echo "Cleaning reference directory..."
    rm -rf "$REFERENCE_DIR"
fi

backup_file="${LOCALES_FILE}.bak"
cp "$LOCALES_FILE" "$backup_file"

restore_original() {
    if [[ -f "$backup_file" ]]; then
        mv "$backup_file" "$LOCALES_FILE"
        echo "Restored original PlayStoreLocales.kt"
    fi
}
trap restore_original EXIT

write_locales_file() {
    local file="$1"
    shift
    local batch_locales=("$@")

    cat > "$file" << 'KOTLIN_HEADER'
package eu.darken.octi.screenshots

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// @formatter:off

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
KOTLIN_HEADER

    for entry in "${batch_locales[@]}"; do
        local fastlane_locale="${entry%%:*}"
        local android_locale="${entry##*:}"
        echo "@Preview(name = \"$fastlane_locale\", locale = \"$android_locale\", device = DS)" >> "$file"
    done
    echo "annotation class PlayStoreLocales" >> "$file"
    echo "" >> "$file"
    echo "@Retention(AnnotationRetention.BINARY)" >> "$file"
    echo "@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)" >> "$file"
    for entry in "${batch_locales[@]}"; do
        local fastlane_locale="${entry%%:*}"
        local android_locale="${entry##*:}"
        echo "@Preview(name = \"$fastlane_locale\", locale = \"$android_locale\", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)" >> "$file"
    done
    echo "annotation class PlayStoreLocalesDark" >> "$file"
    echo "" >> "$file"
    echo "@Retention(AnnotationRetention.BINARY)" >> "$file"
    echo "@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)" >> "$file"
    echo "@Preview(name = \"en-US\", locale = \"en\", device = DS)" >> "$file"
    echo "annotation class PlayStoreLocalesSmoke" >> "$file"
    echo "" >> "$file"
    echo "// @formatter:on" >> "$file"
}

TOTAL_BATCHES=$(( (NUM_LOCALES + BATCH_SIZE - 1) / BATCH_SIZE ))
echo "Generating screenshots for $NUM_LOCALES locale(s) in $TOTAL_BATCHES batch(es)."

for ((i = 0; i < NUM_LOCALES; i += BATCH_SIZE)); do
    batch_num=$(( i / BATCH_SIZE + 1 ))
    end=$(( i + BATCH_SIZE ))
    (( end > NUM_LOCALES )) && end=$NUM_LOCALES
    batch=("${LOCALES[@]:i:BATCH_SIZE}")

    echo ""
    echo "=== Batch $batch_num/$TOTAL_BATCHES (locales $((i + 1))-$end of $NUM_LOCALES) ==="
    write_locales_file "$LOCALES_FILE" "${batch[@]}"

    "$GRADLE" --stop >/dev/null 2>&1 || true
    (cd "$PROJECT_DIR" && "$GRADLE" "$GRADLE_TASK" --no-daemon --no-configuration-cache)
done

echo ""
echo "Screenshot generation complete."
echo "Reference images: $REFERENCE_DIR"

if [[ -d "$REFERENCE_DIR" ]]; then
    total_images=$(find "$REFERENCE_DIR" -name "*.png" | wc -l)
    echo "Total images generated: $total_images"

    expected_images=$((NUM_LOCALES * 6))
    if [[ "$CLEAN" == true && "$total_images" -ne "$expected_images" ]]; then
        echo "ERROR: Expected $expected_images generated images after clean run."
        exit 1
    fi
fi

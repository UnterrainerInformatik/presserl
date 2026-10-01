#!/usr/bin/env bash
# Copies the brand icons from icons/ into every consumer (design D2 of app-icon-and-favicon).
# With --check nothing is copied: lists every copy that is missing or differs and exits 1 if any.
set -euo pipefail
cd "$(dirname "$0")/.."

reader=backend/src/main/resources/META-INF/resources/reader/icons
admin_web=admin/composeApp/src/wasmJsMain/resources
android_res=admin/androidApp/src/main/res/drawable
play=admin/androidApp/play/graphics

# source in icons/ → copy
mapping=(
    "favicon.svg                         $reader/favicon.svg"
    "favicon.ico                         $reader/favicon.ico"
    "favicon-180.png                     $reader/apple-touch-icon.png"
    "favicon.svg                         $admin_web/favicon.svg"
    "favicon.ico                         $admin_web/favicon.ico"
    "android/ic_launcher_foreground.xml  $android_res/ic_launcher_foreground.xml"
    "android/ic_launcher_monochrome.xml  $android_res/ic_launcher_monochrome.xml"
    "play-icon-512.png                   $play/icon-512.png"
    "feature-graphic-de.png              $play/feature-1024x500-de-DE.png"
    "feature-graphic-en.png              $play/feature-1024x500-en-US.png"
)

check=false
case "${1:-}" in
    "") ;;
    --check) check=true ;;
    *) echo "usage: icons/sync.sh [--check]" >&2; exit 2 ;;
esac

outdated=0
for entry in "${mapping[@]}"; do
    read -r source copy <<<"$entry"
    if $check; then
        if [[ ! -f "$copy" ]]; then
            echo "missing:  $copy (from icons/$source)"
            outdated=1
        elif ! cmp -s "icons/$source" "$copy"; then
            echo "differs:  $copy (from icons/$source)"
            outdated=1
        fi
    else
        mkdir -p "$(dirname "$copy")"
        cp "icons/$source" "$copy"
        echo "copied:   icons/$source -> $copy"
    fi
done

if $check; then
    if [[ $outdated -ne 0 ]]; then
        echo "Brand icon copies are outdated; run icons/sync.sh." >&2
        exit 1
    fi
    echo "All brand icon copies match icons/."
fi

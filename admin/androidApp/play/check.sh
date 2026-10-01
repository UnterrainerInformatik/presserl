#!/usr/bin/env bash
# Checks the Google Play store listing in this folder against Play's limits:
# text lengths per language, icon, feature graphics and phone screenshots.
# Needs ImageMagick (identify). Exit code 1 when anything is off.
set -uo pipefail

cd "$(dirname "$0")"
LANGUAGES=(de-DE en-US)
MIN_SCREENSHOTS=4
errors=0

fail() { echo "FAIL $*"; errors=$((errors + 1)); }
ok() { echo "ok   $*"; }

# Play counts characters, not bytes; the trailing newline of the file does not count.
check_text() {
    local file=$1 limit=$2
    if [ ! -s "$file" ]; then fail "$file missing or empty"; return; fi
    local length
    length=$(tr -d '\n' < "$file" | wc -m)
    if [ "$length" -gt "$limit" ]; then fail "$file has $length characters, limit $limit"; else ok "$file $length/$limit"; fi
}

# check_image <file> <exact WxH or empty> <max bytes> <alpha allowed: yes|no>
check_image() {
    local file=$1 size=$2 max_bytes=$3 alpha=$4
    if [ ! -f "$file" ]; then fail "$file missing"; return; fi
    local format dimensions has_alpha bytes
    read -r format dimensions has_alpha < <(identify -format '%m %wx%h %A\n' "$file[0]" 2>/dev/null)
    bytes=$(stat -c %s "$file")
    case "$format" in PNG|JPEG) ;; *) fail "$file is ${format:-unreadable}, not PNG or JPEG"; return ;; esac
    if [ -n "$size" ] && [ "$dimensions" != "$size" ]; then fail "$file is $dimensions, expected $size"; return; fi
    if [ "$bytes" -gt "$max_bytes" ]; then fail "$file has $bytes bytes, limit $max_bytes"; return; fi
    if [ "$alpha" = no ] && [ "$has_alpha" != False ] && [ "$has_alpha" != Undefined ]; then fail "$file has an alpha channel"; return; fi
    ok "$file $format $dimensions"
}

# Phone screenshots: each side 320..3840 px, the long side at most twice the short side, at most 8 MB.
check_screenshot() {
    local file=$1
    check_image "$file" "" $((8 * 1024 * 1024)) no
    local w h
    read -r w h < <(identify -format '%w %h\n' "$file[0]" 2>/dev/null)
    [ -n "${w:-}" ] || return
    local short=$((w < h ? w : h)) long=$((w < h ? h : w))
    if [ "$short" -lt 320 ] || [ "$long" -gt 3840 ]; then fail "$file sides ${w}x${h} outside 320..3840"; fi
    if [ "$long" -gt $((2 * short)) ]; then fail "$file aspect ${w}x${h} longer than 2:1"; fi
}

for language in "${LANGUAGES[@]}"; do
    check_text "listing/$language/title.txt" 30
    check_text "listing/$language/short-description.txt" 80
    check_text "listing/$language/full-description.txt" 4000
done

check_image graphics/icon-512.png 512x512 $((1024 * 1024)) yes

for language in "${LANGUAGES[@]}"; do
    check_image "graphics/feature-1024x500-$language.png" 1024x500 $((15 * 1024 * 1024)) no

    shopt -s nullglob
    screenshots=(graphics/phone-screenshots/"$language"/*.png graphics/phone-screenshots/"$language"/*.jpg)
    shopt -u nullglob
    if [ "${#screenshots[@]}" -lt "$MIN_SCREENSHOTS" ]; then
        fail "graphics/phone-screenshots/$language has ${#screenshots[@]} screenshots, at least $MIN_SCREENSHOTS needed"
    fi
    for screenshot in "${screenshots[@]}"; do check_screenshot "$screenshot"; done
done

if [ "$errors" -gt 0 ]; then echo "$errors problem(s)"; exit 1; fi
echo "Store listing OK"

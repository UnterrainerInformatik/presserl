#!/usr/bin/env bash
# Regenerates every brand icon variant in icons/ (see README.md). The Python venv with fontTools and
# brotli is created on first use in icons/.venv (git-ignored).
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -x .venv/bin/python ]]; then
    python3 -m venv .venv
    .venv/bin/pip install --quiet "fonttools==4.66.1" "brotli==1.2.0"
fi
.venv/bin/python gen.py
echo "Regenerated icons/. Distribute with icons/sync.sh."

#!/usr/bin/env bash
# امضای APK باید همان کلید ثابتِ مخزن (app/board-debug.keystore) باشد؛ وگرنه به‌روزرسانی روی نسخهٔ قبلی «App not installed» می‌دهد.
set -euo pipefail
APK="${1:?مسیر APK}"
APKSIGNER=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
EXPECTED=$(keytool -list -keystore app/board-debug.keystore -storepass android 2>/dev/null | grep 'SHA-256' | sed 's/.*: //' | tr -d ':' | tr 'A-F' 'a-f')
ACTUAL=$("$APKSIGNER" verify --print-certs "$APK" | grep 'certificate SHA-256 digest' | head -1 | sed 's/.*: //' | tr 'A-F' 'a-f')
echo "expected=$EXPECTED"
echo "actual=$ACTUAL"
test -n "$EXPECTED" && test "$EXPECTED" = "$ACTUAL"

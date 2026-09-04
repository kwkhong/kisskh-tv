#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
activity="app/src/main/java/com/lyra/kisskhtv/MainActivity.kt"

required_manifest=(
  'android.permission.INTERNET'
  'android.software.leanback'
  'android.hardware.touchscreen'
  'android.intent.category.LEANBACK_LAUNCHER'
  'android:usesCleartextTraffic="false"'
)

for token in "${required_manifest[@]}"; do
  grep -Fq "$token" "$manifest" || { echo "Missing manifest requirement: $token"; exit 1; }
done

required_activity=(
  'WebView'
  'WebChromeClient'
  'onShowCustomView'
  'onHideCustomView'
  'CookieManager'
  'setAcceptThirdPartyCookies'
)

for token in "${required_activity[@]}"; do
  grep -Fq "$token" "$activity" || { echo "Missing activity requirement: $token"; exit 1; }
done

echo "Static Android TV/WebView checks passed."

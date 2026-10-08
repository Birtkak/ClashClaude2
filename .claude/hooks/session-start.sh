#!/bin/bash
# Prepares a Claude Code cloud session to build, test and playtest the game:
# installs the Android SDK (containers start without one) and warms Gradle caches.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK="${ANDROID_HOME:-/opt/android-sdk}"
CLT_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/clt.zip" "$CLT_ZIP_URL"
  unzip -q -o "$tmp/clt.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

if [ ! -f "$SDK/platforms/android-34/android.jar" ] || [ ! -d "$SDK/build-tools/34.0.0" ]; then
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses > /dev/null || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
    "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /dev/null
fi

cd "$CLAUDE_PROJECT_DIR"
echo "sdk.dir=$SDK" > local.properties
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$SDK\"" >> "$CLAUDE_ENV_FILE"
fi

# Download Gradle and the app/playtest dependencies now so later builds are fast.
# Failures here (e.g. a rate-limited mirror) shouldn't block the session.
./gradlew --quiet :app:compileDebugKotlin || echo "warning: app dependency warm-up failed" >&2
(cd playtest && ../gradlew --quiet compileKotlin) || echo "warning: playtest warm-up failed" >&2

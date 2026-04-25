#!/usr/bin/env bash
# Manual replay of .github/workflows/build.yml inside a runner-images
# Docker container. Mirrors what GitHub Actions does on a `push` to master:
#   - actions/setup-java@v4 (temurin 17)
#   - android-actions/setup-android@v3
#   - ./gradlew make makePluginsJson
#
# Run via:
#   docker run --rm -v "$(pwd)":/repo -w /repo \
#     ghcr.io/catthehacker/ubuntu:act-22.04 bash scripts/ci-build.sh
set -euo pipefail

echo "==> Step 1/4 — Install OpenJDK 17 (mirrors setup-java@v4 distribution=temurin)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -qq -y --no-install-recommends \
  openjdk-17-jdk-headless ca-certificates wget unzip curl git
update-alternatives --auto java >/dev/null
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version

echo "==> Step 2/4 — Install Android SDK (mirrors setup-android@v3)"
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"
# Pin a known-good cmdline-tools build to keep the script reproducible.
CMDLINE_TOOLS_ZIP=commandlinetools-linux-11076708_latest.zip
if [ ! -d latest ]; then
  wget -q "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
  unzip -q "${CMDLINE_TOOLS_ZIP}"
  mv cmdline-tools latest
  rm "${CMDLINE_TOOLS_ZIP}"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

echo "==> Step 3/4 — Configure repo"
cd /repo
chmod +x gradlew
# local.properties is gitignored — synthesize one inside the container.
cat > local.properties <<EOF
sdk.dir=$ANDROID_HOME
EOF

echo "==> Step 4/4 — gradlew make makePluginsJson"
# When the repo is checked out on Windows, gradlew/.sh files come over with
# CRLF endings that break /usr/bin/env. Strip the carriage returns in-place
# (this is what GitHub Actions does implicitly because Linux-side checkout
# has core.autocrlf=false). Idempotent.
sed -i 's/\r$//' gradlew
./gradlew make makePluginsJson --no-daemon --console=plain --stacktrace

echo
echo "==> Build artifacts:"
find . -name '*.cs3' -not -path './build/intermediates/*' 2>/dev/null
ls -la build/plugins.json 2>/dev/null || echo "(plugins.json not at top-level build/)"

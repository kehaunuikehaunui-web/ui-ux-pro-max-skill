#!/usr/bin/env bash
# Builds dist/poche-xpf.apk without Gradle: aapt2 + javac + d8 + zipalign + apksigner.
# Needs: JDK 11+, python3, and an Android SDK with platforms;android-34 and build-tools;34.0.0
#   ANDROID_HOME=/path/to/sdk ./build.sh
# Signing key: $POCHE_KEYSTORE (default ./poche-xpf.keystore, created if missing, never committed).
# Keep that file: an update signed with another key will not install over the old app.
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
VERSION_CODE="${VERSION_CODE:-7}"
VERSION_NAME="${VERSION_NAME:-1.6}"
BT="$ANDROID_HOME/build-tools/34.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
KS="${POCHE_KEYSTORE:-$PWD/poche-xpf.keystore}"
KS_PASS="${POCHE_KEYSTORE_PASS:-pochexpf}"

rm -rf build && mkdir -p build/gen build/classes build/dex dist
POCHE_VERSION="$VERSION_NAME" python3 make_assets.py build/assets

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  -A build/assets --java build/gen build/res.zip \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR" \
  -d build/classes build/gen/com/pochexpf/app/R.java src/com/pochexpf/app/*.java
"$BT/d8" --min-api 24 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias poche \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Poche XPF" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias poche \
  --out dist/poche-xpf.apk build/aligned.apk
"$BT/apksigner" verify dist/poche-xpf.apk
echo "OK -> $(pwd)/dist/poche-xpf.apk ($(du -h dist/poche-xpf.apk | cut -f1))"

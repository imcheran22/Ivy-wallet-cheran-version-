#!/bin/bash
# Generates a local signing keystore for Google Drive distribution.
# Run this once before building the 'local' variant:
#   ./scripts/generate-local-keystore.sh
#
# Then build with: ./gradlew assembleLocal

KEYSTORE_FILE="local-release.jks"
ALIAS="ivy-local"
PASSWORD="ivywallet2025"
VALIDITY_DAYS=10000

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore '$KEYSTORE_FILE' already exists. Delete it first to regenerate."
    exit 0
fi

KEYTOOL_CMD="keytool"
if ! command -v keytool &>/dev/null; then
    # Windows: try Android Studio's bundled JDK
    for dir in \
        "/c/Program Files/Android/Android Studio/jbr/bin" \
        "/c/Program Files/Android/Android Studio/jre/bin" \
        "$JAVA_HOME/bin" \
        "$ANDROID_STUDIO_DIR/jbr/bin"; do
        if [ -f "$dir/keytool" ] || [ -f "$dir/keytool.exe" ]; then
            KEYTOOL_CMD="$dir/keytool"
            break
        fi
    done
fi

if ! command -v "$KEYTOOL_CMD" &>/dev/null && [ ! -f "$KEYTOOL_CMD" ] && [ ! -f "${KEYTOOL_CMD}.exe" ]; then
    echo "ERROR: keytool not found."
    echo "Add your JDK bin directory to PATH, or set JAVA_HOME."
    echo "On Windows, try: export PATH=\"\$PATH:/c/Program Files/Android/Android Studio/jbr/bin\""
    exit 1
fi

"$KEYTOOL_CMD" -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=Ivy Wallet Local, OU=Mobile, O=IvyWallet, L=Local, ST=Local, C=US"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to generate keystore."
    exit 1
fi

echo ""
echo "Keystore generated: $KEYSTORE_FILE"
echo "Build the APK with: ./gradlew assembleLocal"
echo "The APK will be at: app/build/outputs/apk/local/app-local.apk"

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

keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=Ivy Wallet Local, OU=Mobile, O=IvyWallet, L=Local, ST=Local, C=US"

echo ""
echo "Keystore generated: $KEYSTORE_FILE"
echo "Build the APK with: ./gradlew assembleLocal"
echo "The APK will be at: app/build/outputs/apk/local/app-local.apk"

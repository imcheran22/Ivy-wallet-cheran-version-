# Notification Mirror

An Android app that mirrors notifications from a secondary phone to a primary phone using Firebase Cloud Messaging. Both phones run the same app — one in **Sender** mode, the other in **Receiver** mode.

## Architecture

```
Secondary Phone (Sender)              Cloud                    Primary Phone (Receiver)
┌─────────────────────┐    HTTPS     ┌──────────────┐   FCM   ┌─────────────────────┐
│ NotificationListener ├────────────►│ Cloud Function ├───────►│ FCM onMessage       │
│ Service              │             │ (Node.js)      │        │ → local notification │
└─────────────────────┘              └──────────────┘          └─────────────────────┘
```

### Feature isolation

This app is designed to coexist with other features sharing the same Firebase project and FCM token:

- All FCM messages include `"feature": "notif_mirror"` — the `FirebaseMessagingService` dispatches on this key first, so other feature handlers are never invoked for mirror payloads and vice versa.
- Mirrored notifications use a dedicated FCM topic (`notif_mirror_<id>`) and a dedicated `NotificationChannel` (`mirrored_notifications`).
- All settings are stored in a separate `SharedPreferences` file (`notif_mirror_prefs`).
- The sender's `NotificationListenerService` skips its own package's notifications to avoid loops.

To add handlers for other features, edit `NotifMirrorFcmService.kt` and add branches in the `when (feature)` block.

## Setup

### 1. Create a Firebase project

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project (or use an existing one).
2. Add an Android app with package name `com.notifmirror`.
3. Download the generated `google-services.json` and replace `app/google-services.json` with it.

### 2. Deploy the Cloud Function

```bash
cd cloud-functions
npm install -g firebase-tools
firebase login
firebase use YOUR_PROJECT_ID   # or edit .firebaserc
cd functions && npm install && cd ..
firebase deploy --only functions
```

Copy the deployed function URL (shown in the deploy output) — you'll enter it in the app on the Sender phone.

### 3. Generate a signing keystore

```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mykey
```

### 4. Add GitHub Secrets

In your repository's **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w0 release-key.jks` (base64-encode the keystore file) |
| `KEYSTORE_PASSWORD` | The keystore password you chose |
| `KEY_ALIAS` | `mykey` (or whatever alias you used) |
| `KEY_PASSWORD` | The key password you chose |

### 5. Build the APK

Push to `main` or trigger the workflow manually from the **Actions** tab. The signed APK will be available as a workflow artifact named `notif-mirror-release`.

### 6. Install on both phones

1. Download the APK artifact from GitHub Actions onto each phone (e.g., via browser).
2. On each phone, enable **Install unknown apps** for the app you used to download (e.g., Chrome).
3. Install the APK.
4. Open the app and follow the setup wizard:

**On the Sender (secondary phone):**
- Choose **Sender** mode.
- Generate a Topic ID and note it down.
- Enter the Cloud Function URL from step 2.
- Grant **Notification Access** when prompted (the app deep-links to the correct settings page).
- Allow the battery optimization exemption when prompted.

**On the Receiver (primary phone):**
- Choose **Receiver** mode.
- Enter the **same Topic ID** from the Sender.
- Allow the battery optimization exemption when prompted.

That's it — notifications from the secondary phone will now appear on the primary phone.

## Permissions

The app requests only what it needs:

- **Notification Access** (Sender only): to read notifications from other apps. Granted manually via Android Settings.
- **POST_NOTIFICATIONS** (Android 13+): to display mirrored notifications on the Receiver.
- **INTERNET**: to communicate with the Cloud Function and FCM.
- **FOREGROUND_SERVICE**: to keep the listener alive in the background.
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**: to prompt (not force) the user to exempt the app from Doze.

The app has a visible launcher icon, appears in Settings → Apps, and all permissions are granted transparently by the user.

## Project structure

```
app/
  src/main/
    java/com/notifmirror/
      NotifMirrorApp.kt              # Application class, notification channels
      service/
        NotifListenerService.kt      # Captures notifications (Sender)
        NotifMirrorFcmService.kt     # FCM dispatcher + mirror handler (Receiver)
        MirrorForegroundService.kt   # Persistent foreground service
      ui/
        MainActivity.kt             # Setup wizard + status screen
      util/
        MirrorPrefs.kt              # Namespaced SharedPreferences
    res/
      layout/activity_main.xml      # All setup screens in one layout
    AndroidManifest.xml
  google-services.json               # ← Replace with yours
cloud-functions/
  functions/index.js                  # Cloud Function source
  firebase.json
.github/workflows/build.yml          # CI workflow
```

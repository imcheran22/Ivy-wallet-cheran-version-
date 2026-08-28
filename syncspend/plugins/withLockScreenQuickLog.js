const {
  AndroidConfig,
  withAndroidManifest,
  withDangerousMod,
  withStringsXml,
} = require('expo/config-plugins');
const fs = require('fs');
const path = require('path');

const TILE_CLASS = 'QuickLogTileService';
const DEEP_LINK = 'syncspend://quick-log';

/**
 * Lock-screen entry for Quick Log.
 *
 * Android gives third-party apps no lock-screen widget on phones — they were
 * removed in 5.0 and the 16 revival is tablet/dock only. The supported route is
 * a Quick Settings tile, which the shade exposes on the lock screen, launching
 * an activity flagged `showWhenLocked` so it draws over the keyguard instead of
 * demanding an unlock first.
 *
 * The tile fires a deep link rather than a private intent action, so the same
 * URL serves the launcher shortcut and anything added later, and JS reads it
 * through expo-linking with no bridging of our own.
 */
module.exports = function withLockScreenQuickLog(config) {
  config = withAndroidManifest(config, (cfg) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(cfg.modResults);

    const mainActivity = (app.activity ?? []).find(
      (a) => a.$['android:name'] === '.MainActivity',
    );
    if (!mainActivity) {
      throw new Error('withLockScreenQuickLog: .MainActivity not found in the manifest');
    }
    mainActivity.$['android:showWhenLocked'] = 'true';

    mainActivity['meta-data'] = mainActivity['meta-data'] ?? [];
    if (!mainActivity['meta-data'].some((m) => m.$['android:name'] === 'android.app.shortcuts')) {
      mainActivity['meta-data'].push({
        $: { 'android:name': 'android.app.shortcuts', 'android:resource': '@xml/shortcuts' },
      });
    }

    app.service = app.service ?? [];
    if (!app.service.some((s) => s.$['android:name'] === '.' + TILE_CLASS)) {
      app.service.push({
        $: {
          'android:name': '.' + TILE_CLASS,
          'android:exported': 'true',
          'android:icon': '@drawable/ic_quick_log_tile',
          'android:label': 'Log expense',
          'android:permission': 'android.permission.BIND_QUICK_SETTINGS_TILE',
        },
        'intent-filter': [
          { action: [{ $: { 'android:name': 'android.service.quicksettings.action.QS_TILE' } }] },
        ],
      });
    }

    return cfg;
  });

  config = withStringsXml(config, (cfg) => {
    // A static shortcut's labels must be string resources, not literals.
    cfg.modResults = AndroidConfig.Strings.setStringItem(
      [
        { $: { name: 'quick_log_short_label', translatable: 'false' }, _: 'Log expense' },
        { $: { name: 'quick_log_long_label', translatable: 'false' }, _: 'Log an expense' },
      ],
      cfg.modResults,
    );
    return cfg;
  });

  return withDangerousMod(config, [
    'android',
    async (cfg) => {
      const pkg = cfg.android?.package;
      if (!pkg) throw new Error('withLockScreenQuickLog: android.package is not set');
      const root = cfg.modRequest.platformProjectRoot;

      write(
        path.join(root, 'app/src/main/java', ...pkg.split('.'), TILE_CLASS + '.kt'),
        tileService(pkg),
      );
      write(path.join(root, 'app/src/main/res/drawable/ic_quick_log_tile.xml'), TILE_ICON);
      write(path.join(root, 'app/src/main/res/xml/shortcuts.xml'), shortcuts(pkg));
      return cfg;
    },
  ]);
};

function write(file, contents) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
}

const tileService = (pkg) => `package ${pkg}

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that opens Quick Log. Reachable from the lock screen by
 * pulling the shade down, which is as close to a lock-screen button as Android
 * offers a third-party app on a phone.
 *
 * MainActivity carries android:showWhenLocked, so the flow draws over the
 * keyguard rather than sending the user through an unlock first.
 */
class ${TILE_CLASS} : TileService() {
    override fun onClick() {
        super.onClick()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${DEEP_LINK}")).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // startActivityAndCollapse(Intent) was deprecated in 14 in favour of a
        // PendingIntent; the old overload throws there rather than warning.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
`;

const TILE_ICON = `<?xml version="1.0" encoding="utf-8"?>
<!-- Tinted by the system, so it must be a single-colour silhouette. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M11,5h2v14h-2z" />
    <path
        android:fillColor="@android:color/white"
        android:pathData="M5,11h14v2h-14z" />
</vector>
`;

const shortcuts = (pkg) => `<?xml version="1.0" encoding="utf-8"?>
<!-- Long-press the launcher icon. Resource XML gets no manifest placeholder
     substitution, so the package is written in literally. -->
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="quick-log"
        android:enabled="true"
        android:icon="@drawable/ic_quick_log_tile"
        android:shortcutShortLabel="@string/quick_log_short_label"
        android:shortcutLongLabel="@string/quick_log_long_label">
        <intent
            android:action="android.intent.action.VIEW"
            android:data="${DEEP_LINK}"
            android:targetPackage="${pkg}"
            android:targetClass="${pkg}.MainActivity" />
    </shortcut>
</shortcuts>
`;

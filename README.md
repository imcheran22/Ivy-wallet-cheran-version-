# Tracker

A personal money tracker for Android. Track income, expenses, accounts,
budgets, planned payments and loans, with everything stored on the device.

## Install

Every push to the working branch builds a signed APK and attaches it to a
[GitHub Release](../../releases). On the phone: download the `.apk`, open it,
and allow installs from your browser when Android asks.

## Logging from the lock screen

Android gives no lock-screen *widget* slot to apps that do not ask for one, so
all three widgets declare `home_screen|keyguard` and can be placed on either.

| Widget | What it does |
|---|---|
| Add transaction | Income / Expense / Transfer, each straight into the entry screen |
| Add transaction (compact) | The same, in a narrower cell |
| Wallet balance | Current balance at a glance |

Add one from the lock screen's widget picker, or long-press the home screen →
Widgets → Tracker.

## Build

```bash
./gradlew assembleDemo     # sideloadable APK, signed with debug.jks
./gradlew test             # unit tests
./scripts/lint.sh          # Android lint
./scripts/detekt.sh        # static analysis
```

`demo` is the build type to use for real installs: minified and shrunk like
release, but signed with the committed keystore, so it needs no secrets.

## Layout

| Path | What lives there |
|---|---|
| `app/` | Application, root activity, navigation graph, DI wiring |
| `feature/` | One module per screen (home, accounts, budgets, transactions, …) |
| `shared/` | Data, domain, design system, navigation |
| `widget/` | Home and lock screen widgets |
| `temp/` | Legacy code and the old design system, still being retired |

## Naming

The app id is `com.cheran.tracker` and the display name is `Tracker`, both set
in `app/build.gradle.kts`. The Kotlin namespace is still `com.ivy.*`; that only
decides where `R` is generated and renaming it would touch every file for no
visible gain.

## Origin and licence

This is a fork of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet), which
is licensed under the GNU General Public License v3.0. The `LICENSE` file and
this note stay: GPL-3.0 is a copyleft licence, so a fork inherits its terms —
including the obligation to keep the licence and to offer source to anyone the
app is distributed to. That costs nothing for a personal build, and removing
them would not be lawful.

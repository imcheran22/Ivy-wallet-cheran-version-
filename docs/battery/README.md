# Battery monitor

A full battery-health suite built into Ivy Wallet, so there's no need for a
second app on the phone. Reachable from the battery pill in the home header
(it shows the live level) or by navigating to `BatteryScreen`.

Everything is local: telemetry lives in its own SQLite database
(`ivy-battery.db`), never touches the wallet database, and is never uploaded.

## What it measures

| Area | What you get |
| --- | --- |
| Live | level, charge/discharge current, power (W), voltage, temperature, technology, firmware health, plug type, battery-saver state, coulomb + energy counters, deep sleep vs awake |
| Charging | live charge speed, % gained, mAh delivered, duration, avg/peak temperature, per-session history |
| Discharging | screen-on and screen-off drain measured **separately** (%/h and mA), screen-time split, deep-sleep ratio, per-session history |
| Health | measured capacity vs design capacity, wear %, full-equivalent charge cycles, best/worst measurement, capacity-over-time chart |
| Apps | per-app share of each discharge session's screen-on energy |
| History | level / temperature / current charts over 24 h, 7 d or 30 d, CSV export |
| Alarms | charge alarm at a target level, low-battery alarm, overheating alarm - each with sound and vibration |

## How capacity and health are measured

Android exposes no battery-health API, so health is derived the same way
dedicated battery apps do it:

1. During a charge, the charge that actually enters the battery is measured -
   from the coulomb counter (`BATTERY_PROPERTY_CHARGE_COUNTER`) when the device
   has one, otherwise by integrating `BATTERY_PROPERTY_CURRENT_NOW` over time.
2. That energy is divided by the fraction of the range the charge covered, which
   extrapolates to a full-battery capacity.
3. The result is compared against the design capacity, read from the framework's
   hidden `PowerProfile` (with a manual override in **Health → Calibration**).

Single measurements are noisy, so the reported figure is a weighted mean of up
to 20 recent measurements, weighted by how much of the range each covered, with
implausible values discarded. Charges covering less than the configured minimum
range (20% by default) are recorded but not used.

## How per-app drain is attributed

`BATTERY_STATS` is a system permission, so no third-party app can read real
per-app battery accounting. What *is* measurable is foreground time, via the
usage-access special permission. Each discharge session's measured screen-on
energy is split across apps in proportion to their foreground time. The UI says
so plainly - it's an attribution model, not a hardware measurement, and
background work with the screen off is invisible to it.

## Background sampling

Two mechanisms, deliberately overlapping:

- `BatteryMonitorService` - a `specialUse` foreground service that samples on a
  timer *and* on every relevant broadcast (`ACTION_BATTERY_CHANGED`, power
  connected/disconnected, screen on/off). A sample is only written when
  something actually changed, so the database doesn't fill with duplicates.
- `BatteryMonitorWorker` - a periodic (15 min) WorkManager job that records a
  sample on its own and restarts the service. This is what keeps history moving
  when a device refuses the foreground start (Android 12+ background-start
  restrictions) or when an OEM kills the service.

`BatteryBootReceiver` restarts monitoring after a reboot or app update;
`PowerConnectionReceiver` guarantees the plug/unplug transitions that bound
every session are captured.

If sessions still keep getting cut short, exempt Ivy Wallet from battery
optimisation - there's a shortcut for it in **Settings → Monitoring**.

## Permissions

| Permission | Why |
| --- | --- |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | background sampling |
| `POST_NOTIFICATIONS` | the monitor notification (required for the service) and alarms |
| `RECEIVE_BOOT_COMPLETED` | resume monitoring after a reboot |
| `VIBRATE`, `WAKE_LOCK` | alarms |
| `PACKAGE_USAGE_STATS` | per-app drain; special access, granted by the user in Android settings |

## Device quirks

- **Current unit** - the platform documents microamps, but plenty of OEMs report
  milliamps. The reader guesses; **Settings → Units** lets you pin it.
- **Current sign** - some devices report charging as negative. The reader
  corrects it from the charge status, and there's a manual invert switch.
- **No coulomb counter** - capacity falls back to integrating current, which is
  noticeably noisier. The Overview tab says so when this applies.
- **Design capacity unreadable** - `PowerProfile` is a hidden API and can fail.
  Enter the value from the spec sheet in **Health → Calibration**.

## Code layout

```
feature/battery/
  data/          Room database, DAOs, DataStore settings, repository
  domain/        reader, capacity/health/time estimators, session aggregation
  service/       foreground service, worker, receivers, notifications, alarms
  ui/            Material You screen: overview, charging, discharge, health,
                 history, apps, settings
```

`SessionAggregator` and `HealthCalculator` are pure Kotlin and hold all the
measurement logic, so they can be reasoned about (and tested) without a device.

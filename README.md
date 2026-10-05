# Kynox

Android device performance toolkit, built from `PRD.md`. Kotlin + Jetpack
Compose, manual dependency injection (no Hilt/KAPT, keeps the Termux build
simple), root access abstracted behind a single `RootExecutor`, every
system-value write follows Check → Backup → Write → Verify → Log.

**This file is written as a handoff.** If you're picking this project up
fresh (including another AI assistant), read this whole file before touching
code -- especially "Lessons learned / gotchas", which lists real bugs already
hit and fixed once. Repeating them wastes a full Termux build cycle each time.

> **2.3.0 note:** per-app automation now restores the previous profile and refresh
> rate when the app is closed; notification thresholds/cooldown, persistent monitor
> history, backup/restore and unit tests were added (see CHANGELOG.md). These changes
> were statically checked only (no Android SDK or Kotlin compiler available when they
> were written) -- run `./gradlew testDebugUnitTest assembleDebug` first.

> **2.2.0 note:** the visual identity was redesigned at the user's request
> (dark navy base, gradient accent, pill buttons, floating nav, HUD overlays).
> The "calm light" notes below describe 2.1 and are superseded for look and
> feel; see CHANGELOG.md. 2.2.0 changes were statically checked only (no
> Android SDK available when they were written) -- build before trusting.

## Status: what's done vs what's left

Done (all real, working code -- nothing here is a stub):

- **Core:** root detection (Magisk/KernelSU/APatch), `RootExecutor`
  (`Process.waitFor(timeout, unit)`-bounded, not `withTimeoutOrNull` -- see
  gotchas), dynamic sysfs capability discovery with root-fallback reads,
  cached where the underlying fact is fixed hardware (core count, GPU base
  path, power_supply list).
- **Home dashboard:** live battery/CPU/GPU/RAM, realtime custom-Canvas
  graphs, root status pill + logo in the top bar.
- **Device tab:** Device Info, CPU Manager, GPU Manager, Thermal Manager
  (+ Disable Thermal), Battery & Charging (+ **Fast Charging as a persistent
  root daemon**, see below), Performance Profiles (Balanced/Performance/
  Gaming/Powersave/Custom, apply-on-boot via a `BOOT_COMPLETED` receiver),
  Root Manager, Logs.
- **Gaming tab:**
  - Game Mode: manual toggle, applies the Gaming profile, restores the
    previous one when turned off.
  - Game Library (`gaming/library`): lists launchable apps via `<queries>`
    (no `QUERY_ALL_PACKAGES`), flags `ApplicationInfo.CATEGORY_GAME` apps,
    lets the user manage any app manually. This is the managed list the
    auto-detection service watches.
  - Auto Game Mode (Deteksi Otomatis switch on the Gaming tab): a real
    foreground `Service` (`GameDetectionService`) polls the foreground app
    every 3s (paused while the screen is off) via
    `data/gaming/ForegroundAppReader.kt` (root `dumpsys activity activities`,
    falling back to `dumpsys window`). `GameDetectionRepository` holds the
    logic: a managed game in front turns Game Mode on, and after 3
    consecutive polls (~9s) on any other app it restores the previous
    profile. Kynox's own screen is ignored so opening Kynox from a game does
    not count as leaving it. It only undoes what it did itself: if Game Mode
    was already on (manual), it is left alone; if the user switches it off
    while a game is open, it will not switch back on until they leave that
    game. Turning the switch off (or stopping the service) restores the
    profile. Root only -- the switch is disabled without root, since
    Game Mode itself needs root to write anything. Not started on boot: the
    service restarts when the Gaming tab is opened with the switch on.
  - Gaming Session (`gaming/session`, history at `gaming/session/history`,
    report at `gaming/session/report/{id}`): pick a
    managed game, start/stop recording. Runs as a real foreground
    `Service` (`GameSessionService`) so it survives Kynox being backgrounded
    during actual play. Samples once/sec: FPS (SurfaceFlinger TimeStats,
    see `FpsReader`), avg CPU freq, GPU freq, CPU temp, battery temp, power.
    While recording, a draggable FPS overlay (`service/FpsOverlay.kt`,
    plain Views in a `TYPE_APPLICATION_OVERLAY` window) shows the live FPS
    with a stop button; the notification also has a stop action. The
    "display over other apps" permission is granted through root
    (`appops set ... SYSTEM_ALERT_WINDOW allow`, see
    `OverlayPermissionRepository`), falling back to a button that opens the
    system settings page. The overlay shows FPS (coloured against its
    recent peak so it means the same on 30/60/120 FPS games), a mm:ss
    timer and the stop button; tapping it toggles a detail line (CPU temp,
    GPU MHz, power) and dragging moves it, both remembered.
    Sessions are kept as one JSON file each in `filesDir/sessions/`, the
    newest 10 (`MAX_HISTORY` in `GameSessionRepository`); the old single
    `kynox_last_session.json` is migrated into it on first read. The history
    list shows app icon, date, duration, average FPS and resolution, with
    per-item delete. Resolution is the game's own render buffer, read
    best-effort from `dumpsys SurfaceFlinger` (`FpsReader.readResolution`)
    and simply omitted when it cannot be found. The report screen has its
    own summary grid and charts with value and time axes
    (`ui/components/ReportChart.kt`), FPS segments coloured by drop level
    using `MINOR_DROP_RATIO`/`SEVERE_DROP_RATIO` from the domain model. It
    is Kynox's own styling on purpose -- **the user explicitly asked for a
    different look from the reference screenshots they sent**, not a copy;
    the history list and per-session reports were asked for explicitly.
  - Thermal Manager: the status card shows what the system is actually
    doing (Aktif / Nonaktif / Sebagian), read back from the zone `mode`
    files, the msm_thermal parameters and the vendor daemons' `init.svc.*`
    properties -- never inferred from "a backup exists", which stays
    behind after a reboot (a stale backup is cleared when everything reads
    normal again). Disable/restore verify by reading back and report the
    outcome under the status. The kernel accepts only the exact words
    `enabled`/`disabled` for a zone's `mode` (an earlier version wrote
    `disable`, which fails with EINVAL). Restore also flips back anything
    still reading disabled, so it works with a lost backup.
  - Monitor tab (`ui/monitor/`): live charts using the same
    `ReportChart` as the session report, sampled on the user's refresh
    interval into a 5 minute rolling buffer.
  - Session history (`SessionHistoryScreen`) is a day-grouped timeline
    with a per-session FPS sparkline and stability bar, deliberately not a
    list of cards -- **the user asked for a look of Kynox's own, not a
    template shared with other apps**; keep it that way.
  - Charging notification (Settings > Notifikasi, off by default): shows
    level, charging current, power, voltage and battery temperature
    (big-text lines, progress bar), refreshed every ~2.5s, **only while a
    charger is actually connected** -- no idle/standby notification. Split
    across two pieces because a manifest `<receiver>` for
    `ACTION_POWER_CONNECTED`/`ACTION_POWER_DISCONNECTED` is silently never
    delivered once the app targets API 26+ (confirmed against Android's own
    "Implicit broadcast exceptions" list, which does not include either
    action -- this is why the first version used an always-on foreground
    service instead):
    - `ChargingJobService` (`JobInfo.setRequiresCharging(true)`, persisted)
      is the documented replacement -- JobScheduler wakes the app the
      moment charging starts, even from fully stopped, with **no
      notification of its own**.
    - `ChargingMonitorService` is the foreground service that actually
      shows the notification, started by the job. It polls BatteryManager
      (no root) every ~2.5s; two consecutive not-charging reads (debounced
      against a brief USB blip) and it stops itself, removing the
      notification, and re-arms a fresh `ChargingJobService` for the next
      connection.
    `ChargingMonitorService.enable()` is the entry point used by the
    Settings toggle, `MainActivity` and `ProfileBootReceiver`: it starts
    the service immediately if already charging, otherwise just arms the
    job. `stopAndDisarm()` (Settings toggle off / Reset) stops any running
    notification and cancels the pending job. Current is assumed to be in
    uA, as everywhere else in the app.
  - Game detection notification (`GameDetectionService`): **cannot** be
    hidden the same way while the feature is on -- Android requires a
    foreground service to show an ongoing notification for as long as it
    runs, and continuous foreground-app polling has no charging-style
    trigger to sleep between events. Minimized as far as Android allows
    instead: `IMPORTANCE_MIN` channel, `VISIBILITY_SECRET` (hidden on the
    lock screen), no lockscreen/status-bar emphasis.
- **Settings:** theme, refresh interval, confirmation toggle, logging
  toggle, charging notification toggle.
- **Bahasa Indonesia, hardcoded.** `values/strings.xml` (the *only* strings
  file -- there is no `values-id`) contains Indonesian text directly. This
  was a deliberate simplification: two earlier attempts at real
  system-locale switching (system locale resource qualifiers, then
  `LocaleManager`) were both reported as "still English" by the user on
  their device, and per their explicit instruction the app now ships
  Indonesian-only rather than relying on any OS locale mechanism. **Do not
  reintroduce locale-switching (`values-id`, `LocaleManager`,
  `attachBaseContext` locale wrapping, a language picker in Settings)
  without the user asking for it again** -- it was tried twice and removed
  twice at their request.
  - Scope boundary: only *static* UI text (labels/titles/buttons/dialogs)
    is Indonesian. Strings generated in the data layer in Kotlin code
    (battery status like `"Charging (USB)"`, log action names, error
    messages) are still English literals -- translating those needs
    Context-based string lookup inside the repositories, not
    `stringResource()`, and hasn't been done.
- **App icon + splash:** the launcher icon is a white rounded "K" on a
  solid blue adaptive background (`drawable/ic_launcher_background.xml`,
  `mipmap-xxxhdpi/ic_launcher_foreground.png`). The same K is drawn in
  Compose by `drawKynoxMark`/`KMark` in `ui/components/KIdentity.kt` (three
  round-capped strokes, no bitmap), and as a stroke vector for the
  notification icon (`drawable/ic_stat_kynox.xml`). The splash
  (`ui/splash/SplashScreen.kt`) is a short fade/scale-in of the mark and
  the wordmark, nothing more.

- **Visual identity ("calm light") -- keep this consistent, do not drift
  back to the old dark/orange look.**
  - Colour (`ui/theme/Color.kt`): light by default (`ThemeMode.LIGHT`),
    dark and follow-system still selectable. One blue accent
    (`#2563EB` light / `#8AB4FF` dark). Status colours (`StatusGood/
    Warning/Danger`) are mid-tone so they read on both themes.
  - Type (`ui/theme/Type.kt`, fonts in `res/font`, OFL licence in
    `assets/licenses`): Poppins only (Regular, Medium, Bold), sentence
    case everywhere -- no all-caps labels, no monospace.
  - Shape/layout: rounded corners (6-28 dp). `SectionCard` is a title above
    a rounded white surface with a hairline border. `HighlightPanel` (tinted
    accent container) holds the one key readout per screen; the dashboard
    uses three ring gauges (CPU/GPU/RAM). `MetricRow` is label left, value
    right. `KynoxTopBar` is a plain left-aligned title; `KynoxBottomBar`
    wraps Material 3 `NavigationBar`. The Device tab is a grouped list of
    icon rows. Buttons go through `KButton`/`KOutlinedButton`/`KTextButton`.
  - System bars follow the in-app theme (`MainActivity`
    `DisposableEffect` + `SystemBarStyle.auto`), not the OS theme.
  - The session report is laid out as verdict panel (average FPS, stability
    %, one plain sentence), drop map strip, FPS chart, FPS distribution,
    then thermal/power/frequency charts.

- **Charge limit / battery protection (PRD section 9/20, Battery screen):**
  pauses charging once capacity reaches a user-set percent (default 80,
  step of 5, 50-100 range) and resumes it `CHARGE_LIMIT_RESUME_HYSTERESIS`
  (3%) below that, so it doesn't rapidly flip right at the threshold.
  Distinct from Fast Charging (which raises the input current limit): this
  pauses charging altogether via whichever of
  `charging_enabled`/`battery_charging_enabled`/`charge_enabled`/
  `input_suspend`/`stop_charging` is writable under each
  `/sys/class/power_supply/<supply>/` -- each node's own on/off polarity is
  recorded explicitly (`chargePauseNodes` in `BatteryRepository`) since it
  isn't the same across all of them. Compatibility (`chargeLimitState()`)
  is probed independently of Fast Charging's nodes; a device can support
  one without the other.
  Enforcement runs inside `ChargingMonitorService`'s existing poll loop
  rather than a second service, specifically so a charging session shows
  one notification, not two -- the tradeoff is that the limit is only
  enforced while that service is running, i.e. while the charging
  notification feature (Settings > Notifikasi) is also on; there's no
  separate root daemon for this like Fast Charging's. Because pausing
  charging itself makes the framework report "not charging", the service
  loop's continue-or-stop condition is `BatteryInfo.isPlugged`
  (`EXTRA_PLUGGED`) rather than the charging status text, or it would stop
  itself the moment it paused charging. On disconnect (or the setting
  being turned off) it resumes charging first, so nothing is left paused
  into the next session.

**Not built yet** (in PRD order):

1. **Per-game profile.** Auto Game Mode always applies the Gaming profile;
   there is no way yet to pick a different profile per managed game. Would
   store a `package -> ProfileType` map next to the managed list and have
   `GameDetectionRepository` apply that instead of the fixed Gaming profile.
2. **Notifications.** There are three foreground-service ones (Gaming
   Session, Auto Game Mode, and the optional charging monitor) but no
   event notifications: PRD-described ones (e.g. "profile applied", thermal
   warnings) are not implemented.
3. **Monitor history beyond the open tab.** The Monitor tab is built (live
   charts of CPU/GPU/RAM/battery/temps with 30s / 1m / 5m windows and a
   pause button), but its buffer only lives while that screen's ViewModel
   does. Persistent history across app restarts would need a background
   sampler and storage.
4. **Backup/Restore screen.** The *mechanism* exists and is used
   internally everywhere (`data/backup/BackupStore.kt` -- every risky
   write backs up the previous value before changing it, and each
   feature's own "Restore"/"Reset" button reads it back). There is no
   user-facing screen to export/import a full settings+profile bundle as a
   file, which is what the PRD describes for this section.
5. Final polish/testing pass per PRD section 23 (this has been ongoing
   throughout, but there's been no dedicated pass).

## Known limitations (by design, not bugs)

- **FPS reading is best-effort.** `FpsReader` runs
  `dumpsys SurfaceFlinger --timestats -dump` and then `-clear -enable` in one
  root call per sample, so each reading covers exactly one window. FPS is
  `totalFrames / window`, where the window length comes from `/proc/uptime`
  read inside the same root command (measuring it app-side skewed FPS about
  5-7% high, and the first sample after `begin()` had a near-zero window and
  produced a 120 FPS outlier -- windows under 0.5s are now dropped). The
  layer is the one whose name contains `<package>/` with the most frames.
  Each read logs `layer/frames/window/fps/reportedAvg` at debug level
  (`logcat | grep FpsReader`) so a mismatch against another FPS tool can be
  diagnosed from real numbers. The text format of the timestats dump is not
  a stable public API. If FPS is reported as unsupported on some device, get
  the raw `su -c "dumpsys SurfaceFlinger --timestats -dump"` output while
  the game is running and fix the parser in `data/gaming/FpsReader.kt`. Root
  is required; without it the report shows FPS as unsupported. A window with
  zero frames (a full freeze) produces no sample rather than a 0.
- **Disable Thermal is one-shot**, not a daemon. Fast Charging *is* now a
  persistent root daemon (below) -- Thermal was not upgraded the same way.
  If asked, mirror the Fast Charging daemon pattern
  (`data/battery/BatteryRepository.kt`'s `buildScript`/`applyFastCharging`)
  into `data/thermal/ThermalRepository.kt`.
- **GPU renderer name** (Device Info screen) depends on a throwaway EGL
  query succeeding; shows "Unknown" on failure rather than guessing.
- Localization scope boundary -- see above.

## How Fast Charging works (persistent root daemon)

Rebuilt from the shell script the user provided, matching its *behavior*
(not just its node list):

- Turning the toggle on writes a script to app-private storage and launches
  it detached as root: `nohup sh '<script>' > /dev/null 2>&1 &`. A real
  background daemon, independent of Kynox being open, the same way the
  reference Magisk module's `service.sh` runs. Loops once a second writing
  every candidate node (current limits across every discovered
  `power_supply`, plus the `fast_charge`/`hvdcp3`/`pd_allowed`-style flags),
  silently skipping nodes this device doesn't have.
- "Active node" shows the first node that actually exists (preferring
  `/sys/kernel/fast_charge/force_fast_charge`), with its live raw value.
- "Advanced: Force maximum charge current" is a separate, extra-warned
  toggle (only enabled once base Fast Charging is on) that additionally
  forces charging-thermal thresholds open
  (`temp_cool`/`temp_warm`/`temp_hot`, `system_temp_level`) -- the riskiest
  part of the reference script, deliberately kept as an extra opt-in rather
  than bundled into the base toggle.
- Turning the base toggle off `pkill`s the daemon and restores every node
  to its pre-first-activation value (backed up once, never overwritten by
  later on/off/adjust cycles).

Both `Fast_Charging.zip` and `Disable-Thermal.zip` (source material, not
part of the build) are Magisk modules that write charger/thermal sysfs
nodes and, for thermal, stop the vendor thermal daemon -- real risk of
overheating/battery stress if misused. Every write in the app is behind a
warning + confirmation + backup + one-tap restore.

## Lessons learned / gotchas (read before editing)

These are real bugs that were hit, diagnosed, and fixed once already during
development. Some **recurred multiple times** because they're easy to
reintroduce without a compiler to catch them (this project is built via
Termux/Gradle on the user's phone -- there is no local toolchain here to
compile-check against, only static review).

1. **Kotlin `package` declaration must exactly match the file's directory
   path.** `ui/KynoxApp.kt` once declared `package com.kynox.gaming`
   instead of `com.kynox.gaming.ui` -- compiled files in the same package
   silently allowed it to resolve internally, but `MainActivity`'s
   `import com.kynox.gaming.ui.KynoxApp` then failed with "Unresolved
   reference: KynoxApp", and the real cause was completely non-obvious
   from that error alone. **Always grep-check** every file's `package`
   line against its path before shipping a change:
   ```bash
   # from app/src/main/java
   python3 -c "
   import re, glob, os
   for f in glob.glob('com/kynox/gaming/**/*.kt', recursive=True):
       declared = re.search(r'^package\s+([\w.]+)', open(f).read(), re.M).group(1)
       expected = os.path.dirname(f).replace('/', '.')
       if declared != expected: print(f, declared, expected)
   "
   ```

2. **Compose "member extension" functions vs regular extension
   functions.** `Modifier.weight()` inside `Row`/`Column` is a *member*
   of `RowScope`/`ColumnScope` (declared inside those interfaces) --
   it's implicitly available with **no import**, and explicitly importing
   `androidx.compose.foundation.layout.weight` actually breaks it (shadows
   with an internal symbol, "Cannot access 'weight': it is internal").
   Meanwhile `Modifier.padding()`, `.fillMaxWidth()`, `.size()`, `.dp`,
   `.sp`, `stringResource()`, `remember{}`, `collectAsState()`,
   `DrawScope.translate{}`/`.scale{}` (used in the splash screen) are all
   genuine top-level extension functions that **do** need an explicit
   import even though they're called via a receiver scope that makes them
   *look* like members. Getting this backwards either way is a compile
   error. When adding a new file, grep it for every dotted call against
   its import list rather than assuming.

3. **`LazyColumn`'s `items(1) { ... }` with more than one top-level
   `SectionCard(...)` (or any composable) inside, with no `Column(...)`
   wrapping them, renders them all stacked on top of each other** (visibly
   "menumpuk" in a screenshot the user sent) instead of stacked vertically.
   **This exact bug was reintroduced three separate times** while adding
   new screens (Battery, Device Info, Profile, then again in GamingScreen,
   then again in SessionReportScreen) because it's an easy pattern to fall
   into when copy-adapting an existing screen. Any `items(1) { }` block
   with 2+ SectionCards **must** wrap them in
   `Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { ... }`.
   Scan for regressions with:
   ```bash
   # from app/src/main/java/com/kynox/gaming
   python3 -c "
   import re, glob
   for f in glob.glob('ui/**/*.kt', recursive=True):
       c = open(f).read()
       for m in re.finditer(r'items\(\d+\)\s*\{', c):
           i = m.end(); depth = 1
           while i < len(c) and depth: depth += (c[i]=='{') - (c[i]=='}'); i += 1
           block = c[m.end():i]
           if len(re.findall(r'SectionCard\(', block)) >= 2 and not re.match(r'\s*(androidx\.compose\.foundation\.layout\.)?Column\(', block.lstrip()):
               print('OVERLAP RISK:', f)
   "
   ```

4. **`withTimeoutOrNull` around a raw blocking `java.io` stream read does
   not actually enforce the timeout.** Coroutine cancellation only takes
   effect at suspension points; a blocking `InputStream.read()`/`readText()`
   has none, so if the underlying process never produces EOF (a wedged `su`
   call, a sysfs node that blocks on read), the "timeout" never fires and
   the IO thread hangs forever -- this was the root cause of a real ANR
   (freeze) on the GPU Manager screen. Fixed in `RootExecutorImpl`,
   `ShellExecutor`, and `RootDetector` by using `Process.waitFor(timeoutMs,
   TimeUnit.MILLISECONDS)` (a genuinely time-bounded Java call) and only
   reading output *after* confirming the process exited, `destroyForcibly()`-ing
   it otherwise. **Never add a new root/shell call site that wraps
   blocking stream I/O in `withTimeoutOrNull` instead of this pattern.**

5. **Some sysfs paths need root even to check existence/read**, on this
   MIUI kernel specifically -- `java.io.File.exists()`/`.canRead()` from
   the app's own (non-root) process silently reports "not found" for paths
   that *do* exist and are readable via `su`. This under-reported GPU
   support and made CPU usage always show "Unknown". Fixed via
   `SysfsAccess.existsWithRoot`/`readWithRoot`/`listChildrenWithRoot`
   (direct check first, root fallback only if it fails). If a *new* screen
   ever shows "Unknown"/"Not supported" for something the device is known
   to have, this is the first thing to check -- is the read going through
   the root-fallback helpers, or a plain direct-only `SysfsAccess` call?

6. **Termux's Gradle downloads a glibc/x86_64 `aapt2` from Google's Maven,
   which cannot execute on-device** (wrong ABI/libc entirely) -- produces a
   nonsensical `AAPT2 ... Syntax error: ")" unexpected` because the shell
   ends up trying to interpret the binary as a script. Fixed once,
   permanently, via `gradle.properties`:
   `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`
   (requires `pkg install aapt aapt2` in Termux once). Don't remove this
   property.

7. **Batch root reads instead of one `su` spawn per sysfs node.** Early
   GPU/Battery code called `SysfsAccess.readWithRoot`/`existsWithRoot`
   once per candidate path in a loop -- for GPU's ~9 devfreq nodes or
   Battery's dozens of per-supply candidate paths, that's dozens of
   separate `su` process spawns *every poll cycle* (every 2s), which the
   user reported as the app feeling "berat" (heavy/laggy). Fixed with
   `SysfsAccess.readMultipleWithRoot(paths, ...)`, which reads many files
   in one `su -c "echo marker; cat path; echo marker; cat path2; ..."`
   call. Also: `CapabilityEngine` now caches results that describe fixed
   hardware (core count, kgsl base path, power_supply list) instead of
   re-probing every poll. Any new repository that reads several root paths
   per refresh cycle should batch them the same way, not loop
   individual root calls.

8. **String resource cross-check.** Every `stringResource(R.string.x)`
   call needs a matching `<string name="x">` in `values/strings.xml` --
   obvious, but easy to typo or to drop a key when merging/refactoring
   (happened once when consolidating to Indonesian-only strings). Check
   before shipping:
   ```bash
   python3 -c "
   import re, glob
   defined = set(re.findall(r'<string name=\"([\w.]+)\">', open('app/src/main/res/values/strings.xml').read()))
   used = set()
   for f in glob.glob('app/src/main/java/**/*.kt', recursive=True):
       used |= set(re.findall(r'R\.string\.(\w+)', open(f).read()))
   print('missing:', used - defined)
   print('unused:', defined - used - {'app_name'})
   "
   ```

9. **Find the game's SurfaceFlinger layer on every sample, never once at
   session start.** The first FPS implementation looked the layer up in
   `begin()` only. Recording is normally started from Kynox *before* the
   game is opened, so the layer did not exist yet, the lookup returned null
   once and FPS stayed "not supported" for the whole session. The timestats
   reader now matches the package on every read.

10. **`items(1) { }` with several top-level children needs a `Column`, even
    when there is only one `SectionCard(` in the block.** `SessionScreen`
    had this bug (cards, radio rows and buttons overlapping) because the
    grep check in #3 only counts `SectionCard(` calls. Any `items(1)` block
    that emits more than one composable of any kind must be wrapped.

11. **GPU frequency fields are in the kernel's native unit, which is Hz on
    kgsl/devfreq (257000000), not kHz.** The `...Khz` names are historical.
    The values are written back to sysfs unchanged, so they are never
    converted in the data layer; every *display* goes through
    `gpuFreqMhz()` (`core/utils/FreqFormat.kt`), which tells Hz from kHz by
    magnitude. Dividing by 1000 directly is what showed "257000 MHz".

Before considering any change "done", run checks 1, 3, and 8 (cheap,
catch real recurring bugs) plus a brace/paren balance check per changed
file (`grep -o '{' f | wc -l` vs `grep -o '}' f | wc -l`, same for parens)
-- these three categories account for the large majority of build failures
hit during development, all invisible to a plain code read.

## Building (from Termux, on-device)

Built 100% from Termux on the user's Poco X3 Pro (Android 13, MIUI 14) --
this chat's own sandbox has no Android SDK and no network to fetch Gradle
dependencies, so nothing here has ever been compiled by the assistant, only
statically reviewed. All testing/build-error feedback comes from the user.

```bash
pkg install openjdk-17 gradle aapt aapt2
# Android SDK cmdline-tools + platform 34 + build-tools must be installed
cp local.properties.example local.properties
# edit local.properties: sdk.dir=<path to the Android SDK inside Termux>

gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

`gradle.properties` already sets `android.aapt2FromMavenOverride` (gotcha
#6 above) -- don't remove it. Install with `pm install -r
app/build/outputs/apk/debug/app-debug.apk` (or `adb install -r ...`), open
Kynox once, grant root when the Magisk/KernelSU/APatch prompt appears (or
use "Re-check root access" on the Root Manager screen if granted after
first open). Only run `gradle clean` when a change actually warrants it
(renamed package, changed resource structure) -- otherwise a plain
`assembleDebug` is faster and has worked fine incrementally throughout.

## Project layout

```
core/root      RootExecutor, root detection (Magisk/KernelSU/APatch)
core/sysfs     capability discovery + root-fallback/batched sysfs access
core/shell     non-root command execution
core/utils     Logger, AppResult, GPU renderer EGL probe
service/       GameSessionService (foreground) + notification channel setup
boot/          ProfileBootReceiver (apply-on-boot)
data/*         one repository per hardware/feature area
domain/model   plain data classes used by the UI
ui/*           one package per screen/feature, Compose + a small ViewModel each
ui/splash      splash animation (KynoxLogoShapes + SplashScreen)
```

## Recent additions

- **Session recording + highlights:** reports now include battery drain
  (percent/hour and mAh/hour), peak CPU/battery temperature with the moment
  it happened, and the lowest FPS. Export from the report screen as PNG,
  PDF (`SessionExporter`, the rendered report cut into A4 pages) or CSV
  (every sample). PDF/CSV go to `Documents/Kynox` through root, like the PNG
  goes to `Pictures/Kynox`. Sessions stay game-only: the picker lists only
  games from the managed list.
- **Touch tuner** (`data/touch`, `ui/touch`): scans the usual touch-panel
  sysfs/procfs folders for sampling-rate / sensitivity / game-mode nodes and
  lets the user change them; the first original value of each node is kept
  so "restore" can undo it. There is no standard interface, so on devices
  whose driver exposes nothing the screen says so.
- **CPU clusters + per-game affinity** (`data/cpu/CpuAffinityRepository`):
  cores are grouped Little/Big/Prime by top frequency. Cores or whole
  clusters can be switched off (core 0 never), and each managed game can be
  pinned to chosen clusters with `taskset` while it is in front (re-applied
  every few seconds; released when the user leaves the game).
- **Overlay size/opacity:** Settings > Overlay sliders, applied to both the
  session FPS overlay and the quick overlay.
- **Always-on monitor** (`MonitorService`, `MonitorRecorder`): samples in the
  background (slower while the screen is off) and keeps 30 minutes of
  history on disk, so the Monitor tab is never empty when opened. Toggle in
  Settings > Monitoring.
- **Quick overlay FPS works for every app**: an app that draws nothing shows
  0 FPS (`FpsReader.sample(..., allowIdle = true)`); session recording keeps
  the strict rule so loading screens don't pollute a report.
- **Notification fixes:** every foreground service now has its own
  notification id (charging and the quick overlay both used 1003, so one
  replaced the other -- the "charging notification sometimes missing" bug;
  refresh-rate and the monitor also clashed on 1004). The always-on monitor
  also starts the charging monitor the moment a charger is plugged in
  instead of waiting for the scheduled job. Game detection no longer
  updates its status text; the only alert is the one-time "profile applied"
  notification, which removes itself after 15 seconds.
- **FPS accuracy (`FpsMeter`)**: FPS is now computed from the presentation
  timestamps of each layer (`dumpsys SurfaceFlinger --latency`), counting
  every frame exactly once and using the frames' own timestamps as the time
  base. The old frame-counter (`FpsReader`, timestats dump + clear) read
  high because the time spent between its two commands was counted as
  frames but not as time; it stays only as an automatic fallback for ROMs
  without `--latency`. Both the quick overlay and session recording use it.
- **Monitor notification is dismissible**: not `ongoing`, minimum priority,
  with a "Matikan monitor" action that also switches the setting off. Android
  14+ lets users swipe a foreground-service notification away while the
  service keeps running; older versions cannot, so the action button is the
  way out there. `MonitorService.start` uses a plain `startService` once
  running and skips `startForeground` on repeat starts, so opening the app
  never brings a dismissed notification back.
- **Core offline diagnostics** (`CpuRepository.setOnline`): the write, its
  exit code and a root read-back are one command, so a failure says whether
  the kernel refused the write or a vendor service undid it.

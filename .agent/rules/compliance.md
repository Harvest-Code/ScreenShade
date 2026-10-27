# 🛡️ ScreenShade — AI Agent Governance & Inviolable Compliance Rules

> **MANDATORY CONSTITUTION FOR ALL AI AGENTS & AUTOMATED CODING ASSISTANTS**  
> This file defines the immutable technical, architectural, legal, and security rules for **ScreenShade (`com.screenshade.app`)**. Any AI agent, LLM, or developer working on this repository MUST strictly follow these rules without exception.

---

## 1. Zero Proprietary Dependencies & F-Droid Pure FOSS Compliance
* **NO Closed-Source / Tracking SDKs:** NEVER introduce proprietary SDKs (e.g. `com.google.android.gms`, Firebase, Google Play Billing, AdMob, Crashlytics, App Center, Facebook SDK, or closed binary `.jar`/`.aar`/`.so` blobs).
* **F-Droid Build Compliance:** NEVER remove or alter the `dependenciesInfo` exclusion block in `app/build.gradle.kts`:
  ```kotlin
  dependenciesInfo {
      includeInApk = false
      includeInBundle = false
  }
  ```
* **Pure Open-Source Dependencies:** Only standard, publicly auditable, permissively licensed (Apache 2.0 / MIT / BSD) dependencies from `mavenCentral()` or `google()` may be added.

---

## 2. Strict Dual-Flavor Separation & Google Play Policy Compliance
* **Separation of Release Variants:** The app maintains two distinct product flavors in `app/build.gradle.kts`:
  * `fdroid`: `ENABLE_DONATIONS = true`, `SUPPORT_URL = "https://ko-fi.com/harvestcode"`
  * `play`: `ENABLE_DONATIONS = false`, `SUPPORT_URL = "https://github.com/harvestcode/ScreenShade"`
* **Zero Out-of-App Payment References in `play`:**
  * NEVER hardcode donation or payment URLs (`ko-fi.com`, `paypal.com`, `patreon.com`, `buymeacoffee.com`, etc.) inside Kotlin or Java source files.
  * Always reference `BuildConfig.SUPPORT_URL`.
  * In the `play` flavor, NEVER display words such as "Donation", "Donate", "Buy me a coffee", or payment links. The UI must present a strictly neutral "Open Source Project / Source Code (GitHub)" message.
  * In `OnboardingDialog.kt`, `cardWizardKofi` MUST remain `View.GONE` when `!BuildConfig.ENABLE_DONATIONS`.

---

## 3. Road Safety, Hard Caps & IPC Defense-in-Depth
* **Immutable 85% Hard-Cap on Dimming:**
  * In `OverlayService.kt`, `AutomationReceiver.kt`, and `PreferencesManager.kt`, the black veil opacity MUST NEVER exceed `0.85f` (85%). Any incoming value MUST be clamped (`.coerceIn(0.0f, 0.85f)`) and checked against `isNaN()` and `isInfinite()`. Never allow total screen blackout under any circumstances.
* **Mandatory Safety Gates:**
  * **`hasAcceptedDisclaimer` Gate:** NEVER bypass the legal disclaimer acceptance requirement. If `!prefs.hasAcceptedDisclaimer`, background receivers (`BootReceiver`, `AutomationReceiver`) MUST NOT start the overlay.
  * **`isExternalAutomationEnabled` Gate:** `AutomationReceiver.kt` MUST NOT execute any command unless `prefs.isExternalAutomationEnabled` is explicitly `true`. External automation MUST remain disabled (`false`) by default for road and device safety.
* **Emergency Fail-Safe Notification:**
  * The foreground service notification of `OverlayService` MUST always provide a direct, single-tap `PendingIntent` action to instantly stop and disable the overlay (`OverlayService.ACTION_STOP`).

---

## 4. Privacy by Design & Zero Network Surface
* **NO Network Permissions:** The app MUST NEVER declare `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, or `ACCESS_WIFI_STATE`. The app must remain 100% offline.
* **Coarse Location Confinement:** `ACCESS_COARSE_LOCATION` is solely for offline solar ephemeris calculation via the local NOAA algorithm (`SolarEngine.kt`). Location data must NEVER leave local private `SharedPreferences`.
* **Zero Backup Exposure:** In `AndroidManifest.xml`, `android:allowBackup` MUST remain set to `"false"` to prevent unauthorized extraction of private coordinates or preferences via ADB or cloud backups.

---

## 5. Clean Logging & Production Diagnostics
* **NO Uncontrolled Logging:** NEVER use `Log.d`, `Log.i`, `Log.w`, `Log.e`, `Log.v`, or `println` in release code.
* **Guarded Stacktraces:** Any `e.printStackTrace()` MUST be strictly enclosed inside `if (BuildConfig.DEBUG) { ... }` blocks to prevent information leakage to Logcat in production release builds.

---

## 6. Terminal & Autonomous Guardrails (Ponytail Senior Mode)
* **NEVER run build, compile, Gradle (`./gradlew`), APK generation, or ADB install commands in the terminal.** The user handles builds and installs manually.
* **NEVER initiate open-ended terminal loops or background build tasks.**
* **Focus edits strictly on files explicitly requested.**

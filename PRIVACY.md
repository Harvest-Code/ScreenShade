# Privacy Policy – ScreenShade

_Last updated: October 24, 2026_

## 1. 100% Offline Operation
ScreenShade operates **entirely offline**. The application **does not declare or request** the `android.permission.INTERNET` permission (nor any other network permissions): it is technically incapable of connecting to the network, transmitting data, or receiving information from external sources.

## 2. Personal Data and Tracking
ScreenShade **does not collect, transmit, or store on remote servers** any personal data, advertising identifiers, usage information, telemetry, diagnostic logs, or crash reports. The application includes no third-party SDKs, advertising libraries, analytical trackers, or profiling systems.

## 3. Local Storage and Security
The only information stored (software brightness preferences, chromatic color filter settings, solar tracking enablement, interface language, graphic theme, legal disclaimer acceptance flags, automation toggle state, and approximate latest geographic coordinates) resides **exclusively within the app's local sandbox** (`SharedPreferences`).
- **Backup Protection:** System backup is explicitly disabled (`android:allowBackup="false"`), preventing automatic or unauthorized data extraction via ADB commands or cloud backups.

- **Data Deletion:** Uninstalling the application or clearing the app's data permanently deletes all stored settings.

## 4. Permissions Used
| Permission | Technical Purpose | Data Transmitted |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | To display the opaque dimming veil and color filters over other applications | None |
| `RECEIVE_BOOT_COMPLETED` | To restore the filter state upon device or head-unit startup (if enabled by the user) | None |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | To keep the background graphic overlay service active | None |
| `POST_NOTIFICATIONS` | To display the service status notification with a fail-safe immediate stop button | None |
| `ACCESS_COARSE_LOCATION` | Used solely upon explicit user action to obtain approximate latitude and longitude to calculate sunrise and sunset using the offline NOAA mathematical algorithm | None: coordinates remain confined to the device and never transit across networks |

> [!NOTE]
> To ensure full compatibility with standard automation apps (Tasker, MacroDroid) and ADB shell commands without requiring special permissions for those apps, `AutomationReceiver` is protected by an internal logical security gate: all incoming broadcast commands are dropped and ignored unless the user has explicitly enabled the "Enable External Automation" switch in the app settings and accepted the legal disclaimer. Additionally, maximum dimming is strictly limited to 85% to prevent accidental or malicious screen blackouts.

## 5. External Links and Release Differentiation (F-Droid vs Google Play)
- In builds distributed via F-Droid and GitHub, the interface may contain a button linking to the developer's Ko-fi page to allow free and voluntary donations in support of open-source development. Navigation to Ko-fi takes place entirely within the user's default web browser and is governed by Ko-fi's privacy policies.

- In builds compiled for the Google Play Store, any links or references to external donation platforms are entirely disabled and removed from the UI.

## 6. Minors and Updates
ScreenShade does not collect data of any kind from any user, including minors. Any future updates to this policy will be published within this file in the project's repository.

## 7. Contacts
For questions or technical reports, you can open an issue on the project's official repository.

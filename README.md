<div align="center">

# HomeCar

**Home Assistant dashboard for Android Auto**

[![Release](https://img.shields.io/github/v/release/RenanEspinar/HomeCar?include_prereleases&label=version)](https://github.com/RenanEspinar/HomeCar/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[Download APK](https://github.com/RenanEspinar/HomeCar/releases) · [Installation (English / Español)](docs/INSTALACION_ANDROID_AUTO.md) · [Report a problem](https://github.com/RenanEspinar/HomeCar/issues)

</div>

![HomeCar running on Android Auto](https://github.com/user-attachments/assets/27b401d4-e12e-415f-b5be-7581de3537c3)

HomeCar displays a configurable Home Assistant dashboard in a dedicated Android Auto interface. It is a community project based on the open-source Fermata Media Player codebase.

> **Current candidate: v1.0.0-rc.2 (pre-release).** This is not a final, universally compatible 1.0.0. Android Auto acceptance varies by Android version, head unit and installation method. The RC2 APK is **debug-signed for testing**; later APKs signed with another key cannot be installed over it without uninstalling the old package.

## What HomeCar provides

- A dedicated Home Assistant WebView with configurable server URL (HTTPS domain or local HTTP address).
- Persistent Home Assistant cookies, application preferences and navigation state, subject to Android WebView and OS lifecycle limits.
- Reuse of the existing WebView while switching between Android Auto apps, reducing avoidable dashboard reloads.
- Standard Android WebView HTTP cache, rather than forcing a network reload on every return.
- WebView navigation and SSL/TLS improvements, plus optional client certificate support (mTLS) requiring device-specific testing.
- Removal of inherited photo/video/media storage permissions from the Android Auto flavor.
- Phone-side check for **published GitHub Releases**, at most once per day, plus a manual check in the server settings. It **does not auto-download or auto-install** APKs.
- A universal APK bundling the dynamic Web module, built with Android SDK 36 and Bundletool.

## Download and install

**Get the APK from the [GitHub Releases page](https://github.com/RenanEspinar/HomeCar/releases).** For RC2, open the **v1.0.0-rc.2** pre-release and download `HomeCar-1.0.0-RC2.apk` from **Assets**.

**Use the `.apk` asset**, not Source code ZIP and not `.apks` (a Bundletool container).

1. Install the APK on the Android phone. If HomeCar is absent from Android Auto, a device-specific installation method may be necessary.
2. Open HomeCar on the **phone**, tap Settings and enter your Home Assistant URL, e.g. `https://home.example.com/` or `http://192.168.1.100:8123/`.
3. Log into Home Assistant, verify access, and configure Android Auto's launcher while the vehicle is parked.
4. If HomeCar is not listed in the Android Auto launcher, follow the [KingInstaller + Shizuku guide](docs/INSTALACION_ANDROID_AUTO.md).

**KingInstaller does not guarantee Android Auto compatibility.** Android Auto may reject the inherited projection service at the system validator, even when APK installation succeeds; see [issue #9](https://github.com/RenanEspinar/HomeCar/issues/9).

### Installer resources

- [KingInstaller (fcaronte, official GitHub)](https://github.com/fcaronte/KingInstaller/releases)
- [Shizuku (RikkaApps, official GitHub)](https://github.com/RikkaApps/Shizuku/releases)
- [Step-by-step installation, debugging, and recovery guide](docs/INSTALACION_ANDROID_AUTO.md)

## RC2 validation status

| Check | Status |
| --- | --- |
| Static checks of RC2 patches | Passed locally |
| `:fermata:bundleAutoDebug` | Built successfully locally |
| Bundletool universal debug APK | Generated and installed locally |
| Home Assistant dashboard in Android Auto | Working in one real-world setup reported by the maintainer |
| Return to dashboard after switching applications in DHU | Observed to preserve the view and return quickly |
| Android Lint | **Not completed** (previous run was interrupted) |
| BMW/Mercedes rotary-controller compatibility | **Not validated**; [PR #8](https://github.com/RenanEspinar/HomeCar/pull/8) under review |
| Compatibility across devices/Android Auto releases | **Not guaranteed**; several open issues |
| Security audit and release signing | **Not complete** |

See [RC2 release notes](CHANGELOG.md) and [test matrix](docs/PRUEBAS_RC2.md).

## How to build

Requirements: JDK shipped with Android Studio, Android SDK/API 36, Android NDK/CMake as required by Gradle, Git, and [Google Bundletool](https://github.com/google/bundletool/releases).

```powershell
git clone https://github.com/RenanEspinar/HomeCar.git
cd HomeCar
.\gradlew.bat :fermata:bundleAutoDebug
```

The Android Auto flavor has a dynamic `web` feature. Convert the built AAB to a **universal** APK:

```powershell
java -jar bundletool.jar build-apks --bundle="fermata/build/outputs/bundle/autoDebug/homecar-1.0.0-rc.2-auto-debug.aab" --output=HomeCar.apks --mode=universal --overwrite
```

Open `HomeCar.apks` as a ZIP archive and extract `universal.apk`. Debug APKs are not production distribution artifacts. For a stable release, use a secure, backed-up signing key and publish a consistently signed APK.

## Architecture

```text
Android Auto projection -> HomeCar CarService -> HomeCar UI -> Android WebView -> Home Assistant
                                                  |
                                                  +-> Android WebView cookies/cache
Phone settings -> Local preference store           +-> Github Releases update check
```

The project retains Fermata-derived Android Auto projection infrastructure. A **Car App Library IoT** template-based interface is a possible future compatibility path, but it cannot embed an arbitrary Lovelace WebView.

## Reporting problems

Use [GitHub Issues](https://github.com/RenanEspinar/HomeCar/issues). Please include your phone model, Android version, Android Auto version, head unit/vehicle, APK version, installer, and precise reproduction steps. Remove Home Assistant URLs, session tokens, credentials, personal details and logs containing private data before posting.

Known investigations: [#1 Security](https://github.com/RenanEspinar/HomeCar/issues/1), [#6 WebView reload](https://github.com/RenanEspinar/HomeCar/issues/6), [#7 Rotary controls](https://github.com/RenanEspinar/HomeCar/issues/7), [#9 Launcher visibility](https://github.com/RenanEspinar/HomeCar/issues/9), [#12 mTLS](https://github.com/RenanEspinar/HomeCar/issues/12), [#14 Fermata coexistence](https://github.com/RenanEspinar/HomeCar/issues/14), [#15 Back button](https://github.com/RenanEspinar/HomeCar/issues/15).

## Credits, safety and license

Maintained by **Renan Espinar**. HomeCar is derived from [Fermata Media Player by Andrey Pavlenko](https://github.com/AndreyPavlenko/Fermata). All inherited copyrights and author attributions remain applicable. Distributed under the [GNU General Public License v3.0](LICENSE). No affiliation or endorsement from Google, Android Auto, Home Assistant or Nabu Casa is implied.

**Do not operate doors, gates, cameras, alarms or other potentially distracting controls while driving.** Configure and test while parked. HomeCar is not a certified automotive safety system.

# Changelog

## [1.0.0-rc.2] — Pre-release candidate (2026-10-31)

**Source/build:** RC2 modifications and local validation, including successful `:fermata:bundleAutoDebug` and Bundletool-generated universal APK. Signed with the developer machine's **debug key**, not a long-term release key.

### Changes from 0.1.1-beta

- Home Assistant WebView lifecycle retention and navigation-state handling to limit reloads between Android Auto apps.
- Cookie and browser session handling improvements; standard WebView cache usage and fewer redundant preference writes.
- Removed inherited photo/video and broad media storage permissions from the Android Auto variant.
- Phone-side GitHub Releases version checks, no more than once per day, and manual check in HomeCar server settings; no auto-install.
- HTTP(S), WebView, permission and TLS security changes, with optional Android client-certificate (mTLS) selection.
- Floating navigation/back improvements prepared for testing.
- Updated README, KingInstaller/Shizuku setup and documented troubleshooting.

### Validation evidence

- Seven initial static checks and twelve RC2 structural checks passed.
- Compiled with Android SDK 36 and generated a universal debug APK.
- APK installed successfully on the maintainer's phone.
- Maintainer reported RC2 working in the car using KingInstaller + Shizuku; Desktop Head Unit test preserved the dashboard when switching away and back.

### Outstanding work (do not close issues based on this release)

- Android Lint not completed in the prior run.
- Android Auto launcher/Google Play Services compatibility on other phones remains inconsistent ([#9](https://github.com/RenanEspinar/HomeCar/issues/9)).
- Rotary knob focus and selection across OEMs not verified ([#7](https://github.com/RenanEspinar/HomeCar/issues/7), [#11](https://github.com/RenanEspinar/HomeCar/issues/11), [PR #8](https://github.com/RenanEspinar/HomeCar/pull/8)).
- Continued mTLS, security, app-back-button and Fermata co-installation testing.
- Production signing, update migration and long-term compatibility remain pending.

### Installing

Download **`HomeCar-1.0.0-RC2.apk`** from this release's assets and follow [docs/INSTALACION_ANDROID_AUTO.md](docs/INSTALACION_ANDROID_AUTO.md). A debug-signed build is meant for testing, and updating to a different signing key can require reinstalling the app.

## [0.1.1-beta]

Previous beta build of HomeCar, retaining Fermata-derived Android Auto projection infrastructure with a customizable Home Assistant dashboard.

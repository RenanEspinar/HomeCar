# HomeCar 1.0.0 RC2 — Testing status

This page records observations from the maintainer's RC2 development session; it is not a certification or cross-device test result.

| Area | Result | Limit |
|---|---|---|
| Android Java / Gradle autoDebug bundle | PASS | AAB build completed on Windows |
| Bundletool universal APK generation | PASS | Signed with local Android debug key |
| APK installation | PASS | Phone accepted RC2 APK |
| Home Assistant on real Android Auto | REPORTED PASS | One environment after KingInstaller + Shizuku |
| WebView view/session after switching apps | REPORTED PASS | DHU test: same view, quick return |
| Photos/media permissions | Static change passed | Runtime permission regression testing limited |
| Updates check | Static change compiled | End-to-end notification on a newer release not tested |
| Android Lint | INCOMPLETE | Prior lint job was interrupted at 96% |
| Other phones and cars | OPEN | See issues #4, #9, #10, #14 |
| BMW and Mercedes rotary controls | OPEN | See #7, #11 and PR #8 |
| Security and mTLS | PARTIAL | See #1 and #12 |

## Reproduction instructions

1. Build `:fermata:bundleAutoDebug`, generate a universal APK and keep a SHA-256 checksum.
2. Install on a test Android phone, log into Home Assistant and open a non-root dashboard view.
3. Open Android Auto via Desktop Head Unit and change to another app (e.g. Maps), then return to HomeCar.
4. Check navigation, visible reloads, login state, logs and crashes; repeat and compare with a cold start.
5. Validate rotary input separately using DHU and hardware, where available.
6. Repeat after device reboot and Android Auto update. Never assume a single successful car proves universal compatibility.

Do not post logs containing private Home Assistant URLs, session cookies or access tokens.

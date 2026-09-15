# HomeCar

### Home Assistant on Android Auto

HomeCar is an open-source Android application that provides direct access to a Home Assistant dashboard from Android Auto through a dedicated full-screen WebView interface.

The goal is simple:

**Car → HomeCar → Home Assistant**

No media browser.  
No playlists.  
No unnecessary menus.  
Just your dashboard.

---

## Features

- Home Assistant dashboard directly inside Android Auto
- Full-screen WebView interface
- Single HomeCar entry in the Android Auto launcher
- Configurable Home Assistant server
- Persistent URL configuration
- Same server configuration on phone and Android Auto
- HTTPS domain support
- Local HTTP/IP support
- Floating settings button
- Session and WebView persistence
- Android Auto DHU support
- Universal APK build support

---

## Use cases

HomeCar can be used as an in-car interface for:

- Garage doors
- Gates
- Lights
- Security cameras
- Alarm systems
- Climate control
- Home status
- Presence information
- Energy monitoring
- Custom Home Assistant dashboards

The interface displayed by HomeCar is completely controlled by your Home Assistant dashboard.

---

## Configuration

Open HomeCar and tap the settings button.

Enter your Home Assistant address.

Examples:

    https://home.example.com/

or:

    http://192.168.1.100:8123/

If no protocol is entered, HomeCar automatically assumes HTTPS.

Example:

    myhome.example.com

becomes:

    https://myhome.example.com/

The configured server is stored locally and reused automatically.

---

## Android Auto

HomeCar provides a dedicated Android Auto interface that opens the configured Home Assistant dashboard.

The current HomeCar build removes the unused media and mirroring launcher entries, leaving a single:

    HomeCar

entry in Android Auto.

HomeCar has been tested using the Android Auto Desktop Head Unit emulator.

Compatibility with physical vehicles can depend on:

- Android version
- Android Auto version
- Vehicle/head unit
- Installation method
- Android Auto developer settings

---

## Installation

Download the latest universal APK from:

**Releases → Assets**

File format:

    HomeCar-vX.X.X-universal.apk

For current beta builds, KingInstaller can be used as the installation method when required by the Android Auto environment.

After installation:

1. Open HomeCar on the phone.
2. Tap the settings button.
3. Enter your Home Assistant URL.
4. Save the configuration.
5. Connect Android Auto.
6. Open HomeCar.

Configure and test the interface while the vehicle is parked.

---

## Building HomeCar

Requirements:

- Android Studio
- Android SDK 36
- Java / Android Studio JBR
- Gradle
- Android NDK
- CMake

Clone:

    git clone https://github.com/RenanEspinar/HomeCar.git
    cd HomeCar

Build the Android Auto debug bundle:

    ./gradlew bundleAutoDebug

HomeCar currently uses a dynamic Web module, so a universal APK can be generated from the AAB using Google's bundletool.

Example:

    java -jar bundletool.jar build-apks \
      --bundle=homecar.aab \
      --output=HomeCar.apks \
      --mode=universal

The resulting:

    universal.apk

contains the Web module required by HomeCar.

---

## Architecture

HomeCar keeps the Android Auto projection infrastructure while replacing the user-facing experience with a dedicated Home Assistant browser.

Simplified architecture:

    Android Auto
         |
         v
    HomeCar CarService
         |
         v
    HomeCar Activity
         |
         v
    WebView
         |
         v
    Home Assistant

The media browser and mirroring launcher entries are disabled in the HomeCar Android Auto build.

---

## Server configuration

HomeCar does not contain a personal Home Assistant address.

The default placeholder is:

    https://homeassistant.example/

Each user configures their own server through the application.

The selected address is stored locally using application preferences.

---

## Current status

### v0.1 Beta

Working:

- Full-screen Home Assistant WebView
- Android Auto DHU
- Configurable Home Assistant URL
- Persistent configuration
- Single Android Auto launcher entry
- Custom HomeCar branding
- Phone interface
- Universal APK generation

Still under testing:

- Physical vehicle compatibility
- Android Auto version compatibility
- Installation methods across Android versions

---

## Roadmap

Planned improvements include:

- First-run configuration screen
- Connection test
- Better error handling
- Reload button
- Connection status
- Optional kiosk mode
- Configurable WebView behavior
- Multiple Home Assistant profiles
- Improved Android Auto compatibility
- Automated GitHub release builds

---

## Project

HomeCar is developed as an independent open-source project.

Developer:

**Renan Espinar**

Repository:

https://github.com/RenanEspinar/HomeCar

Issues and contributions are welcome.

---

## Technical foundation

HomeCar is derived from the open-source Fermata Media Player project created by Andrey Pavlenko.

Fermata provides part of the Android and Android Auto infrastructure on which HomeCar was initially developed.

Original project:

https://github.com/AndreyPavlenko/Fermata

HomeCar substantially changes the intended user experience and focuses specifically on Home Assistant dashboard access.

---

## License

HomeCar is distributed under the **GNU General Public License v3.0**, consistent with the license of the upstream Fermata project.

See:

    LICENSE

for the complete license text.

---

## Disclaimer

HomeCar is an independent community project.

It is not affiliated with or endorsed by:

- Google
- Android Auto
- Home Assistant
- Nabu Casa
- Fermata

Android Auto interfaces should only be configured or operated when it is safe and legal to do so.

---

**HomeCar — Your Home Assistant dashboard, in your car.**
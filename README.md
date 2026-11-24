# cordova-plugin-request-location-accuracy

Cordova plugin for Android and iOS that asks the user to turn on location from inside the app, through the native system dialog. The user does not have to leave the app to change the location settings.

This is a maintained fork of [dpa99c/cordova-plugin-request-location-accuracy](https://github.com/dpa99c/cordova-plugin-request-location-accuracy) by Dave Alden. Version 3.0.0 moves Android to the current Google Play Services API (`SettingsClient`, play-services-location 21) and gives iOS the same result format as Android. See [CHANGELOG.md](CHANGELOG.md).

- [Overview](#overview)
- [Installation](#installation)
- [Usage](#usage)
- [Constants](#constants)
- [Example](#example)
- [Upgrading from 2.x](#upgrading-from-2x)
- [License](#license)

## Overview

### Android

The plugin asks for a location accuracy. When the device location settings do not meet it (location off, or a lower accuracy mode), Google Play Services shows a Yes/No dialog that changes the settings for the user.

- Uses `SettingsClient.checkLocationSettings` from [play-services-location](https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient).
- If Google Play Services is missing, disabled or out of date, the request fails with `ERROR_GOOGLE_API_CONNECTION_FAILED` and the standard Google Play Services dialog is shown so the user can fix it.
- The plugin does not ask for location permission. Ask for it first, for example with [cordova-diagnostic-plugin](https://github.com/dpa99c/cordova-diagnostic-plugin) or [cordova-plugin-geolocation](https://github.com/apache/cordova-plugin-geolocation).

### iOS

iOS has no API to turn Location Services on from an app, or to open the Location Services page in Settings.

When Location Services are off, the plugin starts a location request. iOS then shows its system alert "Turn On Location Services to Allow … to Determine Your Location", with a button that opens Settings. When Location Services are already on, the plugin returns success at once.

- Add `NSLocationWhenInUseUsageDescription` to the app's Info.plist. Without it, iOS shows nothing.
- iOS does not report which button the user pressed, so the plugin returns `SUCCESS_PROMPT_REQUESTED` once the prompt was requested.
- **"Cancel" caveat:** if the user presses "Cancel", iOS will not show the prompt again for this app. To find out whether Location Services were turned on, call `canRequest()` again on the [resume](https://cordova.apache.org/docs/en/latest/cordova/events/events.html#resume) event: when the user went to Settings, the app resumes from the background.

## Installation

```
cordova plugin add https://github.com/gcferraria/cordova-plugin-request-location-accuracy.git#v3.0.0
```

Always pin a tag (`#vX.Y.Z`) so every build uses the same code.

Requirements:
- cordova 10+, cordova-android 10+, cordova-ios 6+.
- Android: play-services-location 21 or later.

### Play Services version

The plugin depends on `com.google.android.gms:play-services-location`, version `21.3.0` by default. If another plugin in the app needs a different version, set the same one here to avoid Gradle conflicts:

```
cordova plugin add https://github.com/gcferraria/cordova-plugin-request-location-accuracy.git#v3.0.0 --variable PLAY_SERVICES_LOCATION_VERSION=21.3.0
```

Versions before 21 do not work with 3.x, because the plugin uses `LocationRequest.Builder` and `Priority`.

See the [Google Play Services release notes](https://developers.google.com/android/guides/releases) for the available versions.

## Usage

The plugin is available as `cordova.plugins.locationAccuracy` after `deviceready`.

Every method accepts callbacks. When you pass no callback, it returns a Promise instead.

### request([successCallback], [errorCallback], [accuracy])

Asks the user to turn on location.

- `accuracy`: a `REQUEST_PRIORITY_*` constant. Android only, ignored on iOS. Default: `REQUEST_PRIORITY_HIGH_ACCURACY`.
- Both callbacks receive `{ code, message }`, where `code` is a `SUCCESS_*` or `ERROR_*` constant.

Results on Android:
- `SUCCESS_SETTINGS_SATISFIED`: the settings already met the request.
- `SUCCESS_USER_AGREED`: the user agreed to change them.
- `ERROR_USER_DISAGREED`: the user said no.
- `ERROR_CANNOT_CHANGE_ACCURACY`: the settings cannot be changed on this device.
- `ERROR_GOOGLE_API_CONNECTION_FAILED`: Google Play Services is not available or returned an error.
- `ERROR_INVALID_ACCURACY`, `ERROR_ALREADY_REQUESTING`, `ERROR_EXCEPTION`.

Results on iOS:
- `SUCCESS_SETTINGS_SATISFIED`: Location Services are already on.
- `SUCCESS_PROMPT_REQUESTED`: the system prompt was requested.
- `ERROR_ALREADY_REQUESTING`, `ERROR_EXCEPTION`.

```js
cordova.plugins.locationAccuracy.request(
    function (result) { console.log("Success", result.code, result.message); },
    function (error) { console.error("Error", error.code, error.message); },
    cordova.plugins.locationAccuracy.REQUEST_PRIORITY_HIGH_ACCURACY
);

// or
const result = await cordova.plugins.locationAccuracy.request();
```

### canRequest([successCallback], [errorCallback])

Returns a boolean. The meaning differs by platform:
- Android: `true` when the app has location permission (`ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`).
- iOS: `true` when Location Services are off and no request is in progress.

You do not need to call it before `request()`.

### isRequesting([successCallback], [errorCallback])

Returns `true` while a request is in progress.

## Constants

Accuracy (Android only):
- `REQUEST_PRIORITY_NO_POWER` (0): no additional power use; only positions that other apps already got.
- `REQUEST_PRIORITY_LOW_POWER` (1): "city" level, about 10 km.
- `REQUEST_PRIORITY_BALANCED_POWER_ACCURACY` (2): "block" level, about 100 m.
- `REQUEST_PRIORITY_HIGH_ACCURACY` (3): the most accurate positions, using GPS.

Success:
- `SUCCESS_SETTINGS_SATISFIED` (0)
- `SUCCESS_USER_AGREED` (1), Android
- `SUCCESS_PROMPT_REQUESTED` (2), iOS

Errors:
- `ERROR_ALREADY_REQUESTING` (-1)
- `ERROR_INVALID_ACTION` (0)
- `ERROR_INVALID_ACCURACY` (1), Android
- `ERROR_EXCEPTION` (2)
- `ERROR_CANNOT_CHANGE_ACCURACY` (3), Android
- `ERROR_USER_DISAGREED` (4), Android
- `ERROR_GOOGLE_API_CONNECTION_FAILED` (5), Android

The constants exist on both platforms, so you can compare codes without checking the platform.

## Example

This example asks for location permission with [cordova-diagnostic-plugin](https://github.com/dpa99c/cordova-diagnostic-plugin) and then asks for high accuracy.

```js
document.addEventListener("deviceready", function () {
    var diagnostic = cordova.plugins.diagnostic;
    var accuracy = cordova.plugins.locationAccuracy;

    diagnostic.requestLocationAuthorization(function (status) {
        if (status === diagnostic.permissionStatus.DENIED_ALWAYS) {
            console.error("Location permission was denied");
            return;
        }
        accuracy.request(function (result) {
            console.log("Location is on:", result.message);
        }, function (error) {
            console.error("Could not turn on location:", error.code, error.message);
            if (cordova.platformId === "android" && error.code !== accuracy.ERROR_USER_DISAGREED) {
                if (window.confirm("Open the Location Settings page to turn on location manually?")) {
                    diagnostic.switchToLocationSettings();
                }
            }
        }, accuracy.REQUEST_PRIORITY_HIGH_ACCURACY);
    }, function (error) {
        console.error(error);
    });
});
```

## Upgrading from 2.x

- Android: play-services-location 21+ and cordova-android 10+ are required. If you set `PLAY_SERVICES_LOCATION_VERSION` to an older version, update it.
- iOS: callbacks now receive `{ code, message }`. Before, success received nothing and error received a string.
- iOS: the success code is `SUCCESS_PROMPT_REQUESTED` (2) when the prompt was requested, and `SUCCESS_SETTINGS_SATISFIED` (0) when Location Services were already on.
- Android: cancelling the Google Play Services error dialog no longer closes the app.

## License

MIT. See [LICENSE](LICENSE).

Copyright (c) 2016 Dave Alden (Working Edge Ltd.)
Copyright (c) 2026 Gonçalo Ferraria

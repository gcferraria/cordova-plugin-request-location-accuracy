# Changelog

## 3.0.0

This fork (https://github.com/gcferraria/cordova-plugin-request-location-accuracy) continues the original plugin by Dave Alden.

### Breaking changes
- Android: requires play-services-location 21 or later (default 21.3.0) and cordova-android 10 or later. The `PLAY_SERVICES_LOCATION_VERSION` variable still lets you pick the version, but versions before 21 no longer compile.
- iOS: callbacks now receive `{ code, message }`, as on Android. Before, the success callback received nothing and the error callback received a string.
- iOS: when the system prompt is requested, the success code is the new `SUCCESS_PROMPT_REQUESTED` (2). When Location Services are already on, it is `SUCCESS_SETTINGS_SATISFIED` (0).

### New
- One JavaScript module for both platforms, with the same constants on Android and iOS.
- `request`, `canRequest` and `isRequesting` return a Promise when called without callbacks.
- `request` uses `REQUEST_PRIORITY_HIGH_ACCURACY` when no accuracy is given.
- Android: native `isRequesting` action, and a native check that rejects a second request while one is in progress.
- A LICENSE file.

### Changed
- Android: uses `SettingsClient` and `LocationRequest.Builder` instead of the removed `GoogleApiClient` and `SettingsApi`. Google Play Services no longer needs to connect when the plugin starts.
- Android: Google Play Services is checked on each request. When it is missing or out of date, the request fails with `ERROR_GOOGLE_API_CONNECTION_FAILED` and the standard Google Play Services dialog is shown.
- Android: `canRequest` uses `cordova.hasPermission` directly instead of reflection.
- iOS: `+[CLLocationManager locationServicesEnabled]` runs off the main thread, as iOS 16+ requires.
- iOS: uses `locationManager:didUpdateLocations:` instead of the removed `didUpdateToLocation:fromLocation:`.
- Repository, issue and package metadata point to the fork.

### Fixed
- Android: cancelling the Google Play Services error dialog no longer closes the app.
- Android: a request no longer stays pending forever when the Activity is stopped or recreated while the dialog is open.
- Android: every request gets its own callback, so a later call (for example `canRequest`) can no longer receive the result of a pending request.

## 2.3.0 and earlier

See the original repository: https://github.com/dpa99c/cordova-plugin-request-location-accuracy

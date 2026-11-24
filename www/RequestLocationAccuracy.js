/**
 *  Request Location Accuracy plugin
 *
 *  Copyright (c) 2016 Dave Alden (Working Edge Ltd.)
 *  Copyright (c) 2026 Gonçalo Ferraria
 *
 *  Licensed under the MIT License. See LICENSE.
 **/
var exec = require('cordova/exec');

var SERVICE = 'RequestLocationAccuracy';

var RequestLocationAccuracy = function () {
    this.requesting = false;
};

/* ----- Accuracy constants (Android only; ignored on iOS) ----- */

/** Best accuracy possible with no additional power use: only positions that other apps already got. */
RequestLocationAccuracy.prototype.REQUEST_PRIORITY_NO_POWER = 0;
/** "City" level accuracy (about 10 km). */
RequestLocationAccuracy.prototype.REQUEST_PRIORITY_LOW_POWER = 1;
/** "Block" level accuracy (about 100 m). */
RequestLocationAccuracy.prototype.REQUEST_PRIORITY_BALANCED_POWER_ACCURACY = 2;
/** The most accurate positions available, using GPS. Default. */
RequestLocationAccuracy.prototype.REQUEST_PRIORITY_HIGH_ACCURACY = 3;

/* ----- Success codes ----- */

/** The location settings already satisfy the request (Android), or Location Services are already on (iOS). */
RequestLocationAccuracy.prototype.SUCCESS_SETTINGS_SATISFIED = 0;
/** The user agreed to change the location settings (Android). */
RequestLocationAccuracy.prototype.SUCCESS_USER_AGREED = 1;
/** The system prompt to turn on Location Services was requested (iOS). iOS does not report what the user chose. */
RequestLocationAccuracy.prototype.SUCCESS_PROMPT_REQUESTED = 2;

/* ----- Error codes ----- */

/** A request is already in progress. */
RequestLocationAccuracy.prototype.ERROR_ALREADY_REQUESTING = -1;
/** Invalid action. */
RequestLocationAccuracy.prototype.ERROR_INVALID_ACTION = 0;
/** Invalid accuracy constant (Android). */
RequestLocationAccuracy.prototype.ERROR_INVALID_ACCURACY = 1;
/** Exception in the native code. */
RequestLocationAccuracy.prototype.ERROR_EXCEPTION = 2;
/** The location settings cannot be changed on this device (Android). */
RequestLocationAccuracy.prototype.ERROR_CANNOT_CHANGE_ACCURACY = 3;
/** The user said no to the location settings dialog (Android). */
RequestLocationAccuracy.prototype.ERROR_USER_DISAGREED = 4;
/** Google Play Services is missing, out of date, disabled, or returned an error (Android). */
RequestLocationAccuracy.prototype.ERROR_GOOGLE_API_CONNECTION_FAILED = 5;

var ERROR_EXCEPTION = RequestLocationAccuracy.prototype.ERROR_EXCEPTION;

/** Normalises a native result into { code, message }. */
function toResult(value, fallbackCode, fallbackMessage) {
    if (value && typeof value === 'object' && typeof value.code !== 'undefined') {
        return value;
    }
    return { code: fallbackCode, message: value ? String(value) : fallbackMessage };
}

/** Runs executor with the callbacks, or returns a Promise when no callback is given. */
function callbackOrPromise(executor, successCallback, errorCallback) {
    if (typeof successCallback === 'function' || typeof errorCallback === 'function' || typeof Promise === 'undefined') {
        executor(successCallback || function () {}, errorCallback || function () {});
        return undefined;
    }
    return new Promise(executor);
}

/**
 * Asks the user to turn on location.
 *
 * - Android: shows the Google Play Services dialog that turns location on with the given accuracy.
 * - iOS: when Location Services are off, asks iOS to show its "Turn On Location Services" prompt.
 *
 * Both callbacks receive an object { code, message }, where code is a SUCCESS_* or ERROR_* constant.
 * Without callbacks, returns a Promise that resolves or rejects with that object.
 *
 * @param {Function} [successCallback]
 * @param {Function} [errorCallback]
 * @param {number} [accuracy] - a REQUEST_PRIORITY_* constant (Android only). Default: REQUEST_PRIORITY_HIGH_ACCURACY.
 * @returns {Promise|undefined}
 */
RequestLocationAccuracy.prototype.request = function (successCallback, errorCallback, accuracy) {
    var _this = this;
    if (typeof accuracy !== 'number') {
        accuracy = _this.REQUEST_PRIORITY_HIGH_ACCURACY;
    }

    return callbackOrPromise(function (resolve, reject) {
        if (_this.requesting) {
            reject({ code: _this.ERROR_ALREADY_REQUESTING, message: 'A request is already in progress' });
            return;
        }
        _this.requesting = true;
        exec(function (result) {
            _this.requesting = false;
            resolve(toResult(result, _this.SUCCESS_SETTINGS_SATISFIED, 'Success'));
        }, function (error) {
            _this.requesting = false;
            reject(toResult(error, ERROR_EXCEPTION, 'Unknown error'));
        }, SERVICE, 'request', [accuracy]);
    }, successCallback, errorCallback);
};

/**
 * Indicates whether a request can be made now. The meaning differs by platform:
 * - Android: true when the app has location permission (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION).
 * - iOS: true when Location Services are off and no request is in progress.
 *
 * @param {Function} [successCallback] - receives a boolean.
 * @param {Function} [errorCallback]
 * @returns {Promise|undefined}
 */
RequestLocationAccuracy.prototype.canRequest = function (successCallback, errorCallback) {
    return callbackOrPromise(function (resolve, reject) {
        exec(function (value) {
            resolve(value === 1 || value === true);
        }, function (error) {
            reject(toResult(error, ERROR_EXCEPTION, 'Unknown error'));
        }, SERVICE, 'canRequest', []);
    }, successCallback, errorCallback);
};

/**
 * Indicates whether a request is in progress.
 *
 * @param {Function} [successCallback] - receives a boolean.
 * @param {Function} [errorCallback]
 * @returns {Promise|undefined}
 */
RequestLocationAccuracy.prototype.isRequesting = function (successCallback, errorCallback) {
    var _this = this;
    return callbackOrPromise(function (resolve, reject) {
        exec(function (value) {
            resolve(_this.requesting || value === 1 || value === true);
        }, function (error) {
            reject(toResult(error, ERROR_EXCEPTION, 'Unknown error'));
        }, SERVICE, 'isRequesting', []);
    }, successCallback, errorCallback);
};

module.exports = new RequestLocationAccuracy();

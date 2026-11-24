/*
 * Request Location Accuracy plugin
 *
 * Copyright (c) 2016 Dave Alden (Working Edge Ltd.)
 * Copyright (c) 2026 Gonçalo Ferraria
 *
 * Licensed under the MIT License. See LICENSE.
 */
package cordova.plugin;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;

/**
 * Asks the user to turn on location (with a given accuracy) through the Google Play Services
 * location settings dialog, using the SettingsClient API (play-services-location 21+).
 */
public class RequestLocationAccuracy extends CordovaPlugin {
    private static final String TAG = "RequestLocationAccuracy";

    /** Request code for the location settings resolution dialog. */
    private static final int REQUEST_CHECK_SETTINGS = 0x4C41; // "LA"

    /** Request code for the Google Play Services error dialog. */
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 0x4C42;

    /** Interval used to build the LocationRequest. Only the priority matters for the settings check. */
    private static final long LOCATION_REQUEST_INTERVAL_MS = 10000L;

    // Accuracy constants (must match www/RequestLocationAccuracy.js)
    static final int REQUEST_PRIORITY_NO_POWER = 0;
    static final int REQUEST_PRIORITY_LOW_POWER = 1;
    static final int REQUEST_PRIORITY_BALANCED_POWER_ACCURACY = 2;
    static final int REQUEST_PRIORITY_HIGH_ACCURACY = 3;

    // Success codes
    static final int SUCCESS_SETTINGS_SATISFIED = 0;
    static final int SUCCESS_USER_AGREED = 1;

    // Error codes
    static final int ERROR_ALREADY_REQUESTING = -1;
    static final int ERROR_INVALID_ACTION = 0;
    static final int ERROR_INVALID_ACCURACY = 1;
    static final int ERROR_EXCEPTION = 2;
    static final int ERROR_CANNOT_CHANGE_ACCURACY = 3;
    static final int ERROR_USER_DISAGREED = 4;
    static final int ERROR_GOOGLE_API_CONNECTION_FAILED = 5;

    /** Callback of the request in progress; null when no request is in progress. */
    private CallbackContext requestContext = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try {
            switch (action) {
                case "request":
                    request(args.optInt(0, REQUEST_PRIORITY_HIGH_ACCURACY), callbackContext);
                    return true;
                case "canRequest":
                    callbackContext.success(hasLocationPermission() ? 1 : 0);
                    return true;
                case "isRequesting":
                    callbackContext.success(isRequesting() ? 1 : 0);
                    return true;
                default:
                    sendError(callbackContext, "Invalid action: " + action, ERROR_INVALID_ACTION);
                    return false;
            }
        } catch (Exception e) {
            sendError(callbackContext, describe(e), ERROR_EXCEPTION);
            return true;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // request
    // ---------------------------------------------------------------------------------------------

    private synchronized boolean isRequesting() {
        return requestContext != null;
    }

    private void request(int requestedAccuracy, CallbackContext callbackContext) {
        final int priority;
        switch (requestedAccuracy) {
            case REQUEST_PRIORITY_NO_POWER:
                priority = Priority.PRIORITY_PASSIVE;
                break;
            case REQUEST_PRIORITY_LOW_POWER:
                priority = Priority.PRIORITY_LOW_POWER;
                break;
            case REQUEST_PRIORITY_BALANCED_POWER_ACCURACY:
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
                break;
            case REQUEST_PRIORITY_HIGH_ACCURACY:
                priority = Priority.PRIORITY_HIGH_ACCURACY;
                break;
            default:
                sendError(callbackContext, "'" + requestedAccuracy + "' is not a valid accuracy constant", ERROR_INVALID_ACCURACY);
                return;
        }

        synchronized (this) {
            if (requestContext != null) {
                sendError(callbackContext, "A request is already in progress", ERROR_ALREADY_REQUESTING);
                return;
            }
            requestContext = callbackContext;
        }

        final Activity activity = cordova.getActivity();
        activity.runOnUiThread(() -> {
            try {
                if (!checkGooglePlayServices(activity)) {
                    return;
                }

                LocationRequest locationRequest = new LocationRequest.Builder(priority, LOCATION_REQUEST_INTERVAL_MS).build();
                LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                        .setAlwaysShow(true)
                        .build();

                // Listeners are not scoped to the Activity: an Activity-scoped listener is dropped
                // when the Activity stops, which would leave the JavaScript callback pending.
                SettingsClient client = LocationServices.getSettingsClient(activity);
                client.checkLocationSettings(settingsRequest)
                        .addOnSuccessListener(response ->
                                finishWithSuccess("All location settings are satisfied.", SUCCESS_SETTINGS_SATISFIED))
                        .addOnFailureListener(e -> onSettingsFailure(cordova.getActivity(), e));
            } catch (Exception e) {
                finishWithError(describe(e), ERROR_EXCEPTION);
            }
        });
    }

    /** Returns true when Google Play Services can be used; otherwise finishes the request with an error. */
    private boolean checkGooglePlayServices(Activity activity) {
        GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
        int status = availability.isGooglePlayServicesAvailable(activity);
        if (status == ConnectionResult.SUCCESS) {
            return true;
        }

        finishWithError("Google Play Services is not available: " + availability.getErrorString(status), ERROR_GOOGLE_API_CONNECTION_FAILED);

        // Offer the standard fix (install, update or enable Google Play Services). Cancelling it no
        // longer closes the app, as it did in version 2.x.
        if (availability.isUserResolvableError(status)) {
            Dialog dialog = availability.getErrorDialog(activity, status, REQUEST_GOOGLE_PLAY_SERVICES);
            if (dialog != null) {
                dialog.show();
            }
        }
        return false;
    }

    private void onSettingsFailure(Activity activity, Exception e) {
        if (!(e instanceof ApiException)) {
            finishWithError(describe(e), ERROR_EXCEPTION);
            return;
        }

        int statusCode = ((ApiException) e).getStatusCode();
        if (statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED && e instanceof ResolvableApiException) {
            try {
                Log.i(TAG, "Location settings are not satisfied. Showing the location settings dialog.");
                cordova.setActivityResultCallback(this);
                ((ResolvableApiException) e).startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
            } catch (IntentSender.SendIntentException | RuntimeException ex) {
                finishWithError("Unable to show the location settings dialog: " + describe(ex), ERROR_CANNOT_CHANGE_ACCURACY);
            }
        } else if (statusCode == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE) {
            finishWithError("Location settings are inadequate and cannot be changed from the app.", ERROR_CANNOT_CHANGE_ACCURACY);
        } else {
            finishWithError("Google Play Services error " + statusCode + " ("
                    + CommonStatusCodes.getStatusCodeString(statusCode) + "): " + describe(e), ERROR_GOOGLE_API_CONNECTION_FAILED);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CHECK_SETTINGS) {
            return;
        }
        if (resultCode == Activity.RESULT_OK) {
            finishWithSuccess("User agreed to make the required location settings changes.", SUCCESS_USER_AGREED);
        } else {
            finishWithError("User chose not to make the required location settings changes.", ERROR_USER_DISAGREED);
        }
    }

    /**
     * Called when the Activity was destroyed while the settings dialog was open (for example after a
     * configuration change or low memory) and Cordova restores the pending callback.
     */
    @Override
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        synchronized (this) {
            requestContext = callbackContext;
        }
    }

    @Override
    public void onDestroy() {
        synchronized (this) {
            requestContext = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------------
    // canRequest
    // ---------------------------------------------------------------------------------------------

    private boolean hasLocationPermission() {
        boolean authorized = cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        Log.v(TAG, "Location permission is " + (authorized ? "granted" : "not granted"));
        return authorized;
    }

    // ---------------------------------------------------------------------------------------------
    // Results
    // ---------------------------------------------------------------------------------------------

    private CallbackContext takeRequestContext() {
        synchronized (this) {
            CallbackContext ctx = requestContext;
            requestContext = null;
            return ctx;
        }
    }

    private void finishWithSuccess(String message, int code) {
        Log.i(TAG, message);
        CallbackContext ctx = takeRequestContext();
        if (ctx != null) {
            ctx.success(result(message, code));
        }
    }

    private void finishWithError(String message, int code) {
        sendError(takeRequestContext(), message, code);
    }

    private static void sendError(CallbackContext ctx, String message, int code) {
        Log.e(TAG, message);
        if (ctx != null) {
            ctx.error(result(message, code));
        }
    }

    private static JSONObject result(String message, int code) {
        JSONObject json = new JSONObject();
        try {
            json.put("code", code);
            json.put("message", message);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return json;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}

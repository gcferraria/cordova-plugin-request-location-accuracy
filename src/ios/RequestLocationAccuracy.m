/*
 * Request Location Accuracy plugin
 *
 * Copyright (c) 2016 Dave Alden (Working Edge Ltd.)
 * Copyright (c) 2026 Gonçalo Ferraria
 *
 * Licensed under the MIT License. See LICENSE.
 */
#import "RequestLocationAccuracy.h"

// Result codes (must match www/RequestLocationAccuracy.js)
static const int SUCCESS_SETTINGS_SATISFIED = 0;
static const int SUCCESS_PROMPT_REQUESTED = 2;
static const int ERROR_ALREADY_REQUESTING = -1;
static const int ERROR_EXCEPTION = 2;

@implementation RequestLocationAccuracy {
    BOOL _locationStarted;
}

@synthesize locationManager;

- (void)pluginInitialize
{
    self.locationManager = [[CLLocationManager alloc] init];
    self.locationManager.delegate = self;
    _locationStarted = NO;
}

#pragma mark - Plugin API

- (void) request:(CDVInvokedUrlCommand*)command
{
    // +[CLLocationManager locationServicesEnabled] can block, so it must not run on the main thread (iOS 16+ warns about it).
    [self.commandDelegate runInBackground:^{
        BOOL servicesEnabled = [CLLocationManager locationServicesEnabled];

        dispatch_async(dispatch_get_main_queue(), ^{
            CDVPluginResult* pluginResult;
            @try {
                if (self->_locationStarted) {
                    pluginResult = [self errorResult:@"Location is already being requested" code:ERROR_ALREADY_REQUESTING];
                } else if (servicesEnabled) {
                    pluginResult = [self okResult:@"Location Services are already enabled." code:SUCCESS_SETTINGS_SATISFIED];
                } else {
                    // With Location Services off, starting location updates makes iOS show its
                    // "Turn On Location Services" alert, which links to the Settings app.
                    [self.locationManager startUpdatingLocation];
                    self->_locationStarted = YES;
                    pluginResult = [self okResult:@"The system prompt to turn on Location Services was requested." code:SUCCESS_PROMPT_REQUESTED];
                }
            } @catch (NSException *exception) {
                pluginResult = [self errorResult:(exception.reason ?: exception.name) code:ERROR_EXCEPTION];
            }
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        });
    }];
}

- (void) isRequesting:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:(_locationStarted ? 1 : 0)]
                                callbackId:command.callbackId];
}

- (void) canRequest:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        BOOL servicesEnabled = [CLLocationManager locationServicesEnabled];
        dispatch_async(dispatch_get_main_queue(), ^{
            BOOL canRequest = !servicesEnabled && !self->_locationStarted;
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:(canRequest ? 1 : 0)]
                                        callbackId:command.callbackId];
        });
    }];
}

#pragma mark - Internals

- (CDVPluginResult*) okResult:(NSString*)message code:(int)code
{
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{ @"code": @(code), @"message": message }];
}

- (CDVPluginResult*) errorResult:(NSString*)message code:(int)code
{
    NSLog(@"RequestLocationAccuracy: %@", message);
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:@{ @"code": @(code), @"message": message }];
}

- (void) stopRequestingLocation
{
    if (_locationStarted) {
        [self.locationManager stopUpdatingLocation];
        _locationStarted = NO;
    }
}

#pragma mark - CLLocationManagerDelegate

- (void)locationManager:(CLLocationManager*)manager didUpdateLocations:(NSArray<CLLocation*>*)locations
{
    [self stopRequestingLocation];
}

- (void)locationManager:(CLLocationManager*)manager didFailWithError:(NSError*)error
{
    NSLog(@"RequestLocationAccuracy: locationManager didFailWithError %@", error.localizedDescription);
    [self stopRequestingLocation];
}

@end

/*
 * Request Location Accuracy plugin
 *
 * Copyright (c) 2016 Dave Alden (Working Edge Ltd.)
 * Copyright (c) 2026 Gonçalo Ferraria
 *
 * Licensed under the MIT License. See LICENSE.
 */
#import <Cordova/CDVPlugin.h>
#import <CoreLocation/CoreLocation.h>

@interface RequestLocationAccuracy : CDVPlugin <CLLocationManagerDelegate>

@property (nonatomic, strong) CLLocationManager* locationManager;

- (void) request:(CDVInvokedUrlCommand*)command;
- (void) isRequesting:(CDVInvokedUrlCommand*)command;
- (void) canRequest:(CDVInvokedUrlCommand*)command;

@end

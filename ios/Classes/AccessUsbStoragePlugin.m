#import "AccessUsbStoragePlugin.h"
#if __has_include(<access_usb_storage/access_usb_storage-Swift.h>)
#import <access_usb_storage/access_usb_storage-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "access_usb_storage-Swift.h"
#endif

@implementation AccessUsbStoragePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftAccessUsbStoragePlugin registerWithRegistrar:registrar];
}
@end

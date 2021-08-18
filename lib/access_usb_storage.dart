import 'dart:async';

import 'package:flutter/services.dart';

class AccessUsbStorage {
  static const MethodChannel _channel = const MethodChannel('access_usb_storage');

  static Future<dynamic> get avilableDevices async {
    final dynamic devices = await _channel.invokeMethod('availableUSBStorageDevices');
    return devices;
  }

  static Future<bool> requestPermission(String deviceName) async {
    try {
      bool? permissionRes = await _channel.invokeMethod<bool>('requestUSBPermission', deviceName);
      return permissionRes ?? false;
    } catch (ex) {
      print('requestPermission ex: $ex');
      throw ex;
    }
  }

  static Future<bool> write(String deviceKey, String relativePath, dynamic content, SavingType savingType) async {
    try {
      String savingTypeStr;
      switch(savingType) {
        case SavingType.BytesData:
          savingTypeStr = "BytesData";
          break;
        
        case SavingType.StringData:
          savingTypeStr = "StringData";
          break;
      }

      bool? succeed = await _channel.invokeMethod('write', {
        'deviceKey': deviceKey, 'relativePath': relativePath,
        'content': content, 'savingType': savingTypeStr
      });

      return succeed ?? false;
    } catch(ex) {
      print('write() ex: $ex');
      return false;
    }
  }

  static Future<dynamic> read(String deviceKey, String relativePath, SavingType savingType) async {
    try {
      String content;
      String savingTypeStr;
      
      switch(savingType) {
        case SavingType.BytesData:
          savingTypeStr = "BytesData";
          break;
        
        case SavingType.StringData:
          savingTypeStr = "StringData";
          break;
      }

      await _channel.invokeMethod('read', {
        'deviceKey': deviceKey, 
        'relativePath': relativePath,
        'content': content,
        'savingType': savingType
      });
    } catch (ex) {
      print('read() ex: $ex');
      return null;
    }
  }
}

// class SavingType {
//   static const String BytesData = 'BytesData';
//   static const String StringData = 'StringData';
// }

enum SavingType {
  BytesData,
  StringData
}
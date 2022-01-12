import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';

typedef Callback<T> = void Function(T event);

class AccessUsbStorage {
  static const MethodChannel _channel = const MethodChannel('access_usb_storage');
  static const EventChannel _eventChannel = const EventChannel('access_usb_state_events');

  static Stream<dynamic>? _eventsStream;

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

  static Future<dynamic> listenToUsbChange({ required Callback<Map<String, dynamic>> callback, Callback<dynamic>? onError }) async {
    try {
      _eventsStream = _eventChannel.receiveBroadcastStream();
      _eventsStream?.listen((e) {
        print("Received event: $e");
        Map<String, dynamic> res = jsonDecode("$e");

        switch (res['event']) {
          case 'Attach':
            res['event'] = UsbEvents.Attach;
            break;
          
          case 'Detached':
            res['event'] = UsbEvents.Detached;
            break;
        }

        callback(res);
      }, onError: onError);
    } catch(ex) {
      print('listenToUsbChange() ex: $ex');
    }
  }

  static Future<dynamic> deleteListener() async {
    print('Deleting _eventsStream listener');
    _eventsStream = null;
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
      String savingTypeStr;
      
      switch(savingType) {
        case SavingType.BytesData:
          savingTypeStr = "BytesData";
          break;
        
        case SavingType.StringData:
          savingTypeStr = "StringData";
          break;
      }

      dynamic res = await _channel.invokeMethod('read', {
        'deviceKey': deviceKey, 
        'relativePath': relativePath,
        'savingType': savingTypeStr
      });
      
      print('Readed: $res');
      return res;
    } catch (ex) {
      print('read() ex: $ex');
      return '';
    }
  }

  static Future<dynamic> delete(String deviceKey, String relativePath) async {
    try {
      await _channel.invokeMethod('delete');
    } catch(ex) {
      print('delete() ex: $ex');
      return null;
    }
  }
}

enum SavingType {
  BytesData,
  StringData
}

enum UsbEvents {
  Attach,
  Detached
}
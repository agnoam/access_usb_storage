import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:access_usb_storage/access_usb_storage.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  dynamic _availableDevices = [];
  String _fileContent = '';

  @override
  void initState() {
    super.initState();

    AccessUsbStorage.listenToUsbChange(callback: (dynamic e) {
      print(e['event'] == UsbEvents.Attach ? 'Attach' : 'Detached');
      _refreshState();
    });

    initUSBDevicesState();
  }

  @override
  void dispose() {
    AccessUsbStorage.deleteListener();
    super.dispose();
  }

  Future<void> initUSBDevicesState() async {
    dynamic devices;

    try {
      await requestPermission();
      devices = await AccessUsbStorage.avilableDevices;
    } catch (ex) {
      print(ex);
      devices = ["Exception was called on usb devices"];
    }

    setState(() {
      _availableDevices = devices;
    });
  }

  Future<void> requestPermission() async {
    await Permission.manageExternalStorage.isLimited || await Permission.manageExternalStorage.isDenied ?
      await Permission.manageExternalStorage.request()
    :
      print('Has manage storage permission already');
    await Permission.storage.isLimited || await Permission.storage.isDenied ? 
      await Permission.storage.request()
    : print('Has storage permission already');
  }

  showAlertDialog(BuildContext context, { String title = "Title", String content = "Content" }) {
    // set up the button
    Widget okButton = TextButton(
      child: Text("OK"),
      onPressed: () { },
    );

    // set up the AlertDialog
    AlertDialog alert = AlertDialog(
      title: Text(title),
      content: Text(content),
      actions: [
        okButton
      ],
    );

    // show the dialog
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return alert;
      },
    );
  }

  Future<void> _saveFile({ String content = 'Test content' }) async {
    try {
      await AccessUsbStorage.write(_availableDevices[0], '/some_generated_file.txt', content, SavingType.StringData);
      print('File have written to the USB storage');
    } catch(ex) {
      print('_saveFile() ex: $ex');
    }
  }

  Future<String> _openFile() async {
    try {
      String content = await AccessUsbStorage.read(_availableDevices[0], '/some_generated_file.txt', SavingType.StringData);
      return content;
    } catch(ex) {
      print('_openFile() ex: $ex');
      return '';
    }
  }

  Future<void> _deleteFile() async {
    try {
      AccessUsbStorage.delete(_availableDevices[0], '/some_generated_file.txt');
      print('File deleted successfuly');
    } catch(ex) {
      print('_deleteFile() ex: $ex');
    }
  }

  Future<void> _listFilesInUSB() async {
    try {
      // Directory? dir = await getExternalStorageDirectory();
      // print(dir?.path);
      // print(dir?.list().toList().toString());

      // Directory usbDir = Directory(_availableDevices[0]);
      // File usb = File(_availableDevices[0]);
      // print('isUSB exists: ${usb.exists()}');

      // List<FileSystemEntity> fs = usbDir.listSync();
      // print('usb fs: ${fs.toString()}');
      // dynamic usbFS = AccessUsbStorage.tryUSBFileSystem(_availableDevices[0]);
      // print(usbFS.toString());
    } catch(ex) {
      print('_listFilesInUSB() ex: $ex');
    }
  }

  Future<void> _refreshState() async {
    initUSBDevicesState();
    setState(() => _fileContent = '');
  }

  @override
  Widget build(BuildContext context) {
    SystemChrome.setSystemUIOverlayStyle(
      SystemUiOverlayStyle(
        statusBarBrightness: Brightness.light
      )
    );

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
          actions: [
            IconButton(
              icon: Icon(Icons.refresh),
              onPressed: () => _refreshState()
            )
          ]
        ),
        body: Center(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Text(_availableDevices.toString()),
              
              _fileContent.isNotEmpty ? 
                Text('Saved file content: $_fileContent')
              : 
                SizedBox()
              ,
              
              _availableDevices.length > 0 ?
                Column(
                  children: [
                    TextButton(
                      child: Text('Create file'),
                      onPressed: () => _saveFile()
                    ),
                    TextButton(
                      child: Text('Read file'),
                      onPressed: () async {
                        String content = await _openFile();
                        setState(() => _fileContent = content);
                      }
                    ),
                    TextButton(
                      child: Text('Delete file'),
                      onPressed: () => _deleteFile()
                    ),
                    TextButton(
                      child: Text('List files'),
                      onPressed: () => _listFilesInUSB()
                    ),
                    TextButton(
                      child: Text('Request USB permission'),
                      onPressed: () => AccessUsbStorage.requestPermission(_availableDevices[0])
                    )
                  ]
                )
              :
                SizedBox()
            ]
          )
        )
      )
    );
  }
}

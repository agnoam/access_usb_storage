import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:access_usb_storage/access_usb_storage.dart';

void main() {
  const MethodChannel channel = MethodChannel('access_usb_storage');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    // expect(await AccessUsbStorage, '42');
  });
}

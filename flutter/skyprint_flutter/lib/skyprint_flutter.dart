import 'package:flutter/services.dart';

/// Một máy in USB tìm thấy qua `UsbManager.getDeviceList()` (Android) --
/// chỉ những thiết bị có USB Printer interface (`UsbConstants.USB_CLASS_PRINTER`).
class SkyprintUsbCandidate {
  const SkyprintUsbCandidate({required this.id, required this.name, required this.address});

  final String id;
  final String name;
  // "vendorId:productId" -- xem skyprint-core's UsbPrinterTransport.
  final String address;

  factory SkyprintUsbCandidate.fromMap(Map<Object?, Object?> map) => SkyprintUsbCandidate(
        id: map['id'] as String,
        name: map['name'] as String,
        address: map['address'] as String,
      );
}

/// Cầu Dart -> skyprint-core (Kotlin) qua MethodChannel, Android-only. Test
/// USB song song với pos_data's UsbPrinterService hiện có -- KHÔNG thay thế.
class SkyprintFlutter {
  static const _channel = MethodChannel('skyprint_flutter/usb');

  static Future<List<SkyprintUsbCandidate>> listUsbCandidates() async {
    final raw = await _channel.invokeMethod<List<Object?>>('listUsbCandidates') ?? const [];
    return raw.map((e) => SkyprintUsbCandidate.fromMap(e as Map<Object?, Object?>)).toList();
  }

  /// Trả về message thành công; ném [PlatformException] nếu in lỗi.
  static Future<String> printUsbTest(SkyprintUsbCandidate printer) async {
    final result = await _channel.invokeMethod<String>('printUsbTest', {
      'address': printer.address,
      'name': printer.name,
    });
    return result ?? 'OK';
  }
}

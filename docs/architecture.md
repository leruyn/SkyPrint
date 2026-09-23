# skyprint — Kiến trúc tổng

Thư viện in hoá đơn ESC/POS đa nền tảng, 2 bản song song:

- **KMP** (`kmp/`) — cho SkytabOffline (Android + iOS).
- **Flutter** (`flutter/`) — cho SkyPos-Flutter (Android + iOS).

Hai bản dùng chung **đặc tả** (docs/features/REQ-*), **mã lỗi**, và **golden fixtures** (`spec/golden/`) — cùng document mẫu phải ra cùng byte ESC/POS ở cả hai bản.

## Ma trận transport × nền tảng

| Transport | Android | iOS | Ghi chú |
|---|---|---|---|
| USB Printer-class | ✅ REQ-004 | ❌ | iOS chặn USB non-MFi |
| LAN TCP 9100 | ✅ REQ-005 | ✅ REQ-005 | iOS cần Local Network permission |
| Bluetooth Classic SPP | ✅ REQ-006 | ❌ | iOS chặn SPP non-MFi |
| BLE GATT | ✅ REQ-007 | ✅ REQ-007 | UUID cấu hình được |

## Lớp

```
┌───────────────────────────── App (SkytabOffline / SkyPos-Flutter) ─────────────────────────────┐
│  ReceiptDocument ──► PrinterService.print(printer, document)                                     │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
        │                                    │
  ┌─────▼──────── core (pure) ─────────┐  ┌───▼──────────── queue (REQ-008) ────────────┐
  │ Document model      (REQ-001)      │  │ 1 hàng đợi / máy in, timeout, retry,        │
  │ EscPosEncoder       (REQ-001)      │  │ PrinterErrorCode thống nhất                 │
  │ AsciiFolding        (REQ-001/002)  │  └───┬─────────────────────────────────────────┘
  │ RasterPacker 1-bit→GS v 0 (REQ-002)│      │ PrinterTransport.open(printer) → PrinterConnection
  └─────▲──────────────────────────────┘  ┌───▼──────────── transports ──────────────────┐
        │ TextRasterizer (platform)       │ Usb (REQ-004) · Lan (REQ-005)                 │
        │ Android Canvas / iOS CoreText / │ BtClassic (REQ-006) · Ble (REQ-007)           │
        │ Flutter dart:ui (REQ-002)       │ PrinterDiscovery (REQ-003)                    │
                                          └───────────────────────────────────────────────┘
```

## Bố cục repo / artifact

```
skyprint/
  spec/golden/                  # document JSON + expected .bin/.png, dùng chung 2 bản
  spec/templates/               # bộ mẫu mặc định (hoá đơn, tạm tính, bếp, huỷ món, ca Z) × 58/80mm
  kmp/
    skyprint-core/              # commonMain: model, encoder, ascii, raster packer, capabilities, errors, queue, contracts
    skyprint-template/          # commonMain: template engine JSON + placeholder (REQ-009)
    skyprint-raster/            # expect/actual TextRasterizer: androidMain (Canvas), iosMain (CoreText)
    skyprint-lan/               # commonMain, ktor-network sockets (Android + iOS)
    skyprint-ble/               # commonMain, Kable (Android + iOS)
    skyprint-android/           # androidMain: USB + Bluetooth Classic + discovery Android
  flutter/
    packages/skyprint_core/     # pure Dart: model, encoder, ascii, raster packer, capabilities, template engine, errors, queue, LAN (dart:io)
    packages/skyprint/          # Flutter plugin: rasterizer dart:ui + MethodChannel
                                #   android/ → dùng lại artifact KMP `skyprint-android` + `skyprint-ble`
                                #   ios/     → Swift CoreBluetooth (BLE)
```

Quyết định chính:

1. **Encoder viết 2 lần (Kotlin + Dart)** — nhỏ (~300 dòng), đổi lại mỗi bản không phụ thuộc runtime bên kia. Golden fixtures đảm bảo không lệch.
2. **Native Android của plugin Flutter dùng lại artifact KMP** — USB/BT/BLE chỉ viết và sửa lỗi 1 lần (Kotlin). Plugin chỉ là adapter MethodChannel mỏng.
3. **iOS của plugin Flutter viết Swift thuần cho BLE** — tránh nhúng Kotlin/Native XCFramework vào Flutter (tăng size, phức tạp CocoaPods). LAN trên Flutter dùng `dart:io` nên không cần native.
4. **Tiếng Việt mặc định raster** (REQ-002), ASCII là fallback.
5. **Mẫu in do skyprint render cho cả online và offline** (REQ-009, chốt 2026-09-23) — không in theo nội dung RK7 server dựng nữa, hoá đơn luôn giống nhau.

## Roadmap

- **v1:** REQ-001..010.
- **v2:** REQ-011 tem nhãn TSPL (khổ tem, gap; transport dùng lại) · trình thiết kế mẫu kéo-thả · đọc trạng thái máy in (DLE EOT) · USB-serial · codepage CP1258.

## Mã lỗi dùng chung (`PrinterErrorCode`)

| Code | Retry? | Nghĩa |
|---|---|---|
| `INVALID_DOCUMENT` | ✗ | Document sai (REQ-001) |
| `INVALID_TEMPLATE` | ✗ | Mẫu in sai cú pháp/biến (REQ-009) |
| `PERMISSION_REQUIRED` | ✗ | Cần xin quyền runtime trước (kèm tên quyền) |
| `PERMISSION_DENIED` | ✗ | Người dùng từ chối |
| `UNSUPPORTED_PLATFORM` | ✗ | Transport không có trên nền tảng này |
| `UNSUPPORTED_DEVICE` | ✗ | Thiết bị không phải máy in / thiếu endpoint/characteristic |
| `NOT_FOUND` | ✗ | Không thấy máy in với id đã lưu |
| `ADAPTER_OFF` | ✗ | Bluetooth tắt |
| `CONNECT_TIMEOUT` | ✓ | Không kết nối kịp |
| `HOST_UNREACHABLE` | ✓ | LAN không tới được |
| `DISCONNECTED` | ✓ | Mất kết nối giữa chừng |
| `WRITE_FAILED` | ✓ | Ghi thất bại |
| `TIMEOUT` | ✗ | Hết tổng thời gian job |
| `CANCELLED` | ✗ | App huỷ job |
| `UNKNOWN` | ✗ | Khác |

## Phiên bản & publish

**Mục tiêu: SDK dùng nội bộ nhiều dự án Dcorp** (chốt 2026-09-23) — không phải
lib riêng của SkytabOffline/SkyPos, cũng chưa phải SDK công khai (không cần
license công cộng, issue tracker mở, chứng nhận đa hãng ngay). Scope nghiệp
vụ (F&B POS) giữ nguyên cho tới khi có dự án cụ thể khác cần thêm (chốt: làm
tổng quát trước, chưa map dự án nào ngoài SkytabOffline + SkyPos-Flutter).

- KMP: Maven (`com.dcorp.skyprint:*`), trước mắt `mavenLocal`/GitLab Package Registry nội bộ; Maven Central chỉ nếu sau này mở public.
- Flutter: git dependency (tag) hoặc pub server nội bộ; pub.dev chỉ nếu mở public.
- Hai bản cùng số version (SemVer) — golden fixtures là hợp đồng giữa chúng.
- **SemVer kỷ luật dù chỉ dùng nội bộ**: đổi API không tương thích ngược →
  tăng major, không được "tiện tay sửa luôn" khi nhiều dự án đã phụ thuộc.
  Deprecate (đánh dấu, giữ hoạt động) ít nhất 1 minor version trước khi xoá.
- Mỗi thay đổi có ý nghĩa ghi vào [CHANGELOG.md](../CHANGELOG.md) (Keep a
  Changelog) — dự án khác nâng version đọc đây trước khi đọc diff.
- CI publish tự động mỗi lần merge vào `main` — **chưa dựng** (repo chưa có
  remote git/host CI, xem lúc quyết định host).

package com.dcorp.skyprint.rastershim

/**
 * Đánh dấu: `skyprint-raster` đã gộp vào `skyprint-core` từ 0.3.0 (package `com.dcorp.skyprint.raster` giữ nguyên).
 *
 * Module cần ít nhất MỘT file nguồn: không có source thì Gradle bỏ qua bước đóng gói klib (iOS) và
 * `generateMetadataFileFor*Publication` lỗi "…-0.3.0.klib (No such file or directory)" lúc publish.
 */
@Deprecated("Dùng com.dcorp.skyprint:skyprint-core; module skyprint-raster chỉ còn để build cũ không vỡ.")
internal object RasterMovedToCore

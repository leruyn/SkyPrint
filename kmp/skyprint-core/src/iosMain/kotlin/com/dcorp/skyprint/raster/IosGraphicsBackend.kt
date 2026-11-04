package com.dcorp.skyprint.raster

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextSetLineDash
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextSetRGBStrokeColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.UIKit.sizeWithAttributes
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.drawAtPoint
import kotlin.math.ceil

/**
 * Bộ vẽ bill bằng UIKit/CoreGraphics (REQ-053) cho iPad Master. Font hệ thống (San Francisco) có dấu tiếng Việt; cỡ chữ là
 * điểm ảnh thật vì ngữ cảnh ảnh dùng scale 1.0. Ảnh cuối chuyển sang thang xám 8-bit để đưa vào [MonoConverter].
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosGraphicsBackend : GraphicsBackend {
    private fun font(style: TextStyle): UIFont =
        if (style.bold) UIFont.boldSystemFontOfSize(style.sizePx.toDouble()) else UIFont.systemFontOfSize(style.sizePx.toDouble())

    private fun attrs(style: TextStyle): Map<Any?, Any?> =
        mapOf(NSFontAttributeName to font(style), NSForegroundColorAttributeName to UIColor.blackColor)

    override val measurer = object : TextMeasurer {
        override fun width(text: String, style: TextStyle): Int =
            ceil((text as NSString).sizeWithAttributes(attrs(style)).useContents { width }).toInt()

        override fun lineHeight(style: TextStyle): Int = ceil(font(style).lineHeight).toInt()

        override fun wrap(text: String, style: TextStyle, maxWidth: Int) = TextWrap.wrap(text, maxWidth) { width(it, style) }
    }

    override fun createSurface(width: Int, height: Int): DrawSurface {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0)
        val ctx = UIGraphicsGetCurrentContext()
        CGContextSetRGBFillColor(ctx, 1.0, 1.0, 1.0, 1.0)
        CGContextFillRect(ctx, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))

        return object : DrawSurface {
            override fun drawText(text: String, x: Int, y: Int, style: TextStyle) {
                (text as NSString).drawAtPoint(CGPointMake(x.toDouble(), y.toDouble()), withAttributes = attrs(style))
            }

            override fun drawHLine(y: Int, x1: Int, x2: Int, thickness: Int, dashed: Boolean) {
                CGContextSetRGBStrokeColor(ctx, 0.0, 0.0, 0.0, 1.0)
                CGContextSetLineWidth(ctx, thickness.toDouble())
                if (dashed) {
                    memScoped {
                        val pattern = allocArray<DoubleVar>(2)
                        pattern[0] = 6.0; pattern[1] = 4.0
                        CGContextSetLineDash(ctx, 0.0, pattern, 2u)
                    }
                } else {
                    CGContextSetLineDash(ctx, 0.0, null, 0u)
                }
                CGContextMoveToPoint(ctx, x1.toDouble(), y.toDouble())
                CGContextAddLineToPoint(ctx, x2.toDouble(), y.toDouble())
                CGContextStrokePath(ctx)
            }

            override fun toGray(): GrayBitmap {
                val image = UIGraphicsGetImageFromCurrentImageContext()
                UIGraphicsEndImageContext()
                val cg = image?.CGImage ?: error("Không lấy được ảnh bill từ CoreGraphics")
                val gray = CGBitmapContextCreate(
                    null, width.toULong(), height.toULong(), 8u, width.toULong(),
                    CGColorSpaceCreateDeviceGray(), CGImageAlphaInfo.kCGImageAlphaNone.value,
                ) ?: error("Không tạo được ngữ cảnh ảnh xám")
                CGContextDrawImage(gray, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cg)
                val data = CGBitmapContextGetData(gray) ?: error("Ngữ cảnh ảnh xám không có dữ liệu")
                return GrayBitmap(width, height, data.reinterpret<ByteVar>().readBytes(width * height))
            }
        }
    }
}

/** iPad Master: bill raster dựng bằng CoreGraphics. */
fun createPlatformReceiptRasterizer(): ReceiptRasterizer = GraphicsReceiptRasterizer(IosGraphicsBackend())

/** Độ lệch múi giờ hiện tại của máy (phút) tại [epochMs]. */
@OptIn(ExperimentalForeignApi::class)
fun platformUtcOffsetMinutes(epochMs: Long): Int =
    (NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)) / 60).toInt()

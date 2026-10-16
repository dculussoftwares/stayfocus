package com.dculus.stayfocused.kids

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A square QR image as ARGB pixels, [size] pixels on each side. */
internal class QrPixels(
    val size: Int,
    val argb: IntArray,
)

/** QR encoding with the ZXing core encoder. The pixel step is pure so it can be tested (and decoded) on the JVM. */
internal object QrCodes {
    /** Quiet zone in modules; the QR spec asks for 4 so stock camera apps lock on reliably. */
    private const val QUIET_ZONE_MODULES = 4

    /** Pixels per module; the bitmap is drawn without smoothing, so modules stay sharp at any size. */
    private const val PIXELS_PER_MODULE = 10

    fun pixels(
        text: String,
        ink: Int,
        light: Int,
        pixelsPerModule: Int = PIXELS_PER_MODULE,
    ): QrPixels {
        val hints =
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            )
        // Width and height 0 ask for the smallest matrix: one entry per module, quiet zone included.
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = matrix.width * pixelsPerModule
        val argb = IntArray(size * size)
        for (y in 0 until size) {
            val row = y / pixelsPerModule
            for (x in 0 until size) {
                argb[y * size + x] = if (matrix.get(x / pixelsPerModule, row)) ink else light
            }
        }
        return QrPixels(size, argb)
    }

    fun bitmap(
        text: String,
        ink: Int,
        light: Int,
    ): ImageBitmap {
        val qr = pixels(text, ink, light)
        return Bitmap.createBitmap(qr.argb, qr.size, qr.size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

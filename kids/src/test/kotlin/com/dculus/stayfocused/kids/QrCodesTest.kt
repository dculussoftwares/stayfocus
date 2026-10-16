package com.dculus.stayfocused.kids

import com.dculus.stayfocused.core.sync.LinkTokens
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import kotlin.test.assertEquals

class QrCodesTest {
    private val ink = 0xFF0E0F0D.toInt()
    private val light = 0xFFF2F4EC.toInt()

    private fun decode(qr: QrPixels): String {
        val source = RGBLuminanceSource(qr.size, qr.size, qr.argb)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun theQrDecodesBackToTheLinkUri() {
        val uri = LinkTokens.uri(LinkTokens.newToken())
        assertEquals(uri, decode(QrCodes.pixels(uri, ink, light)))
    }

    @Test
    fun theDecodedTextIsWhatTheScannerParses() {
        val token = LinkTokens.newToken()
        val text = decode(QrCodes.pixels(LinkTokens.uri(token), ink, light))
        assertEquals(token, LinkTokens.tokenFrom(text))
    }

    @Test
    fun theImageIsSquareAndUsesOnlyInkAndLight() {
        val qr = QrCodes.pixels(LinkTokens.uri(LinkTokens.newToken()), ink, light)
        assertEquals(qr.size * qr.size, qr.argb.size)
        assertEquals(setOf(ink, light), qr.argb.toSet())
    }
}

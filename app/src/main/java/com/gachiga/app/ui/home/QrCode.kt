package com.gachiga.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR 코드 (N5). 폰 카메라가 잘 읽도록 흰 바탕에 검은 점으로 그린다.
 * 정차 중 정산 화면에서만 띄운다 (UI_GUIDELINES.md 3절: QR은 주행 중 금지).
 */
@Composable
fun QrCode(content: String, size: Dp = 360.dp) {
    val matrix: BitMatrix = remember(content) {
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
            ),
        )
    }
    Canvas(Modifier.background(Color.White).padding(16.dp).size(size)) {
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

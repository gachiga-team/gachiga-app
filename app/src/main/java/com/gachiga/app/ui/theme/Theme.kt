package com.gachiga.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 차량 디스플레이 기준: 본문 24sp 이상, 버튼 높이 76dp 이상 (spec.md 6절). */
object CarDimens {
    val buttonHeight = 76.dp
}

private val CarColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    secondary = Color(0xFFFFB74D),
    background = Color(0xFF101418),
    surface = Color(0xFF1A2026),
)

private val CarTypography = Typography(
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 26.sp),
    bodyMedium = TextStyle(fontSize = 24.sp),
    labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun GachiGaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarColors, typography = CarTypography, content = content)
}

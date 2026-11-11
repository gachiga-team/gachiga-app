package com.gachiga.app.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** UI_GUIDELINES.md 2절 수치 기준. 화면은 이 값만 쓴다. */
object CarDimens {
    /** 터치 영역 최소 76×76dp (Android for Cars) */
    val touch = 76.dp
    /** 터치 영역 사이 최소 23dp */
    val gap = 24.dp
    /** 주요 아이콘 44×44dp */
    val icon = 44.dp
    /** 화면 가장자리 여백 */
    val screenPadding = 32.dp

    @Deprecated("touch를 쓴다", ReplaceWith("touch"))
    val buttonHeight = touch
}

/** 어두운 배경 #101418 기준 명암비 4.5:1 이상인 색만 쓴다 (UI_GUIDELINES.md 2절). */
private val CarColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    secondary = Color(0xFFFFB74D),
    background = Color(0xFF101418),
    surface = Color(0xFF1A2026),
)

/** 보조 설명 글자 색. #101418 위에서 명암비 약 6:1. */
val SubtleText = Color(0xFF9AA4AE)

/**
 * Pleos 시스템 폰트 Asta Sans. Pleos에서는 `pretendard`라는 이름의 폰트 묶음으로 등록돼 있다(에뮬레이터에서 확인).
 * 다른 기기처럼 묶음이 없으면 Typeface.create가 기본 폰트를 돌려준다.
 */
private val AstaSans = FontFamily(Typeface.create("pretendard", Typeface.NORMAL))

/** 주요 텍스트 32sp, 보조 텍스트 24sp 이상 (UI_GUIDELINES.md 2절). */
private val CarTypography = Typography(
    displayLarge = TextStyle(fontFamily = AstaSans, fontSize = 64.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = AstaSans, fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = AstaSans, fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = AstaSans, fontSize = 32.sp),
    bodyMedium = TextStyle(fontFamily = AstaSans, fontSize = 24.sp),
    labelLarge = TextStyle(fontFamily = AstaSans, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun GachiGaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarColors, typography = CarTypography, content = content)
}

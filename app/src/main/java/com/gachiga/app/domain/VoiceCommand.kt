package com.gachiga.app.domain

/**
 * 음성으로 받은 문장을 앱 명령으로 바꾼다 (spec.md F8). 순수 Kotlin.
 * STT SDK는 텍스트만 돌려주고 명령을 실행하지 않으므로 해석은 앱이 한다.
 */
sealed interface VoiceCommand {
    /** "여행 시작, 4명". 인원을 못 들으면 null. */
    data class StartTrip(val headcount: Int?) : VoiceCommand
    data object Pause : VoiceCommand
    data object Resume : VoiceCommand
    data object EndTrip : VoiceCommand
    /** "통행료 3천 2백 원" */
    data class AddExpense(val category: ExpenseCategory, val amountWon: Long) : VoiceCommand
    /** 금액만 말한 경우 ("6만 원"). 주유·충전 금액 답으로 쓴다. */
    data class Amount(val amountWon: Long) : VoiceCommand
    data object Yes : VoiceCommand
    data object No : VoiceCommand
    data object Unknown : VoiceCommand

    companion object {
        private val nativeCounts = mapOf(
            "한" to 1, "하나" to 1, "두" to 2, "둘" to 2, "세" to 3, "셋" to 3, "네" to 4, "넷" to 4,
            "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9,
        )
        private val sinoCounts = mapOf(
            "일" to 1, "이" to 2, "삼" to 3, "사" to 4, "오" to 5, "육" to 6, "칠" to 7, "팔" to 8, "구" to 9,
        )
        private val headcountRegex = Regex("([0-9]+|다섯|여섯|일곱|여덟|아홉|하나|한|두|둘|세|셋|네|넷|일|이|삼|사|오|육|칠|팔|구)명")

        fun parse(text: String): VoiceCommand {
            val s = text.replace(" ", "").replace(",", "").replace(".", "")
            if (s.isEmpty()) return Unknown
            return when {
                s.contains("다시시작") || s.contains("재개") || s.contains("다시기록") -> Resume
                s.contains("시작") && (s.contains("여행") || s.contains("출발")) -> StartTrip(headcountOf(s))
                s.contains("여행종료") || s.contains("여행끝") || s.contains("여행마치") || s == "종료" -> EndTrip
                s.contains("일시정지") || s.contains("잠깐멈춰") || s.contains("기록멈춰") -> Pause
                s in setOf("네", "예", "응", "어", "좋아", "그래", "맞아", "끝내", "끝내줘", "종료해") || s.startsWith("네") && s.length <= 3 -> Yes
                s in setOf("아니", "아니요", "아니오", "취소", "아냐", "계속") -> No
                hasExpenseWord(s) -> KoreanAmount.parseExpense(text)?.let { (c, a) -> AddExpense(c, a) } ?: Unknown
                looksLikeAmount(s) -> KoreanAmount.parse(s)?.let { Amount(it) } ?: Unknown
                else -> Unknown
            }
        }

        /** "오늘" 같은 말의 '오'를 5로 읽지 않도록, 원·단위·아라비아 숫자가 있을 때만 금액으로 본다. */
        private fun looksLikeAmount(s: String) =
            s.contains('원') || s.any { it.isDigit() } || listOf('만', '천', '백').any { it in s }

        private fun hasExpenseWord(s: String) =
            listOf("통행", "톨게이트", "하이패스", "주차").any { it in s }

        /** "4명", "세 명"에서 인원(1~9)을 읽는다. */
        fun headcountOf(text: String): Int? {
            val s = text.replace(" ", "")
            val m = headcountRegex.find(s) ?: return null
            // "열두 명"처럼 10명 이상이면 지원 범위(1~9명) 밖이다.
            if (m.range.first > 0 && s[m.range.first - 1] in "열물") return null
            val token = m.groupValues[1]
            val n = token.toIntOrNull() ?: nativeCounts[token] ?: sinoCounts[token] ?: return null
            return n.takeIf { it in 1..9 }
        }
    }
}

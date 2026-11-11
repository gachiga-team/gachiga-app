package com.gachiga.app.domain

/**
 * 음성으로 받은 금액·비용 문장을 읽는다 (spec.md F4, F8). 순수 Kotlin.
 *
 * "삼천이백 원", "3천 2백원", "12,500원", "만 오천 원", "1만2천원"처럼
 * 한글 숫자, 아라비아 숫자, 단위(십·백·천·만·억)를 섞어 써도 읽는다.
 */
object KoreanAmount {

    private val digits = mapOf(
        '영' to 0L, '공' to 0L, '일' to 1L, '이' to 2L, '삼' to 3L, '사' to 4L,
        '오' to 5L, '육' to 6L, '칠' to 7L, '팔' to 8L, '구' to 9L,
    )
    private val smallUnits = mapOf('십' to 10L, '백' to 100L, '천' to 1_000L)
    private val bigUnits = mapOf('만' to 10_000L, '억' to 100_000_000L)

    /** 금액을 원 단위로 돌려준다. 숫자를 찾지 못하면 null. */
    fun parse(text: String): Long? {
        val s = text.replace(",", "").replace(" ", "").removeSuffix("원")
        var total = 0L
        var section = 0L
        var current: Long? = null
        var found = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() -> {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    current = s.substring(i, j).toLongOrNull() ?: return null
                    found = true
                    i = j
                    continue
                }
                c in digits -> { current = digits.getValue(c); found = true }
                c in smallUnits -> {
                    section += (current ?: 1L) * smallUnits.getValue(c)
                    current = null
                    found = true
                }
                c in bigUnits -> {
                    val value = section + (current ?: 0L)
                    total += (if (value == 0L) 1L else value) * bigUnits.getValue(c)
                    section = 0L
                    current = null
                    found = true
                }
                else -> Unit // "원", "정도" 같은 글자는 건너뛴다
            }
            i++
        }
        if (!found) return null
        return total + section + (current ?: 0L)
    }

    /** "통행료 3천 2백 원" → (TOLL, 3200). 종류를 못 찾으면 OTHER. */
    fun parseExpense(text: String): Pair<ExpenseCategory, Long>? {
        val category = when {
            "통행" in text || "톨게이트" in text || "하이패스" in text -> ExpenseCategory.TOLL
            "주차" in text -> ExpenseCategory.PARKING
            else -> ExpenseCategory.OTHER
        }
        val amountText = text
            .replace(Regex("통행료|톨게이트|하이패스|주차비|주차료|주차|기타|비용"), "")
        val amount = parse(amountText) ?: return null
        return category to amount
    }
}

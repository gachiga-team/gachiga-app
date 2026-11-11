package com.gachiga.app.domain

/** 음성 안내 문장의 조사 (예: "강릉으로", "서울로"). 순수 Kotlin. */
object KoreanJosa {

    /** 받침이 있으면 "으로", 없거나 ㄹ 받침이면 "로". 한글이 아니면 "(으)로". */
    fun euro(word: String): String {
        val last = word.trimEnd().lastOrNull() ?: return "로"
        if (last !in '가'..'힣') return if (last.isDigit()) digitEuro(last) else "(으)로"
        val jong = (last - '가') % 28
        return if (jong == 0 || jong == 8) "로" else "으로" // 8 = ㄹ
    }

    /** 단어에 "으로/로"를 붙인다. 예: "강릉" → "강릉으로". */
    fun withEuro(word: String): String = word + euro(word)

    // 숫자로 끝나는 이름 ("3번 출구")은 읽는 소리로 판단: 1(일),3(삼),6(육),7(칠),8(팔),0(영) → 받침
    private fun digitEuro(c: Char): String = when (c) {
        '1', '7', '8' -> "로" // 일, 칠, 팔: ㄹ 받침
        '3', '6', '0' -> "으로"
        else -> "로"
    }
}

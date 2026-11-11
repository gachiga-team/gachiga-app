package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanJosaTest {

    @Test
    fun `받침에 따라 으로와 로`() {
        assertEquals("강릉으로", KoreanJosa.withEuro("강릉"))
        assertEquals("서울로", KoreanJosa.withEuro("서울"))      // ㄹ 받침
        assertEquals("부산으로", KoreanJosa.withEuro("부산"))
        assertEquals("강릉역으로", KoreanJosa.withEuro("강릉역"))
        assertEquals("여수로", KoreanJosa.withEuro("여수"))      // 받침 없음
    }

    @Test
    fun `숫자와 영문`() {
        assertEquals("3번 출구로", KoreanJosa.withEuro("3번 출구"))
        assertEquals("게이트 3으로", KoreanJosa.withEuro("게이트 3"))
        assertEquals("COEX(으)로", KoreanJosa.withEuro("COEX"))
    }
}

package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestAreasTest {
    // 영동고속도로를 따라 서쪽에서 동쪽으로 (OpenStreetMap 좌표)
    private val yeoju = RestArea("여주휴게소 (인천방향)", 37.23937, 127.56977)
    private val munmak = RestArea("문막휴게소 (강릉방향)", 37.33518, 127.85817)
    private val hoengseong = RestArea("횡성휴게소", 37.46486, 128.13462)
    private val areas = listOf(hoengseong, yeoju, munmak)

    @Test fun `목록 파일을 읽는다`() {
        val parsed = RestAreas.parse(sequenceOf("# 출처", "횡성휴게소\t37.46486\t128.13462", "깨진 줄", "\t1\t2"))
        assertEquals(listOf(hoengseong), parsed)
    }

    @Test fun `진행 방향 앞쪽에서 가장 가까운 휴게소를 고른다`() {
        // 여주와 문막 사이에서 동쪽(강릉 쪽)으로 달리는 중
        assertEquals(munmak, RestAreas.pick(areas, 37.28, 127.70, heading = 75.0))
        // 같은 자리에서 서쪽으로 달리면 여주
        assertEquals(yeoju, RestAreas.pick(areas, 37.28, 127.70, heading = 250.0))
    }

    @Test fun `일행이 이미 지나친 휴게소는 고르지 않는다`() {
        // 나는 문막 앞, 2호차는 문막을 지나 횡성 쪽으로 가는 중 → 둘 다 앞에 있는 횡성
        val pick = RestAreas.pick(areas, 37.28, 127.70, heading = 75.0, others = listOf(37.38 to 127.95))
        assertEquals(hoengseong, pick)
    }

    @Test fun `너무 가깝거나 먼 곳뿐이면 고르지 않고 방향을 모르면 가장 가까운 곳을 고른다`() {
        assertNull(RestAreas.pick(areas, 37.335, 127.857, heading = 75.0, maxMeters = 10_000.0)) // 문막 바로 앞, 횡성은 10km 밖
        assertEquals(yeoju, RestAreas.pick(areas, 37.25, 127.62, heading = null))
    }

    @Test fun `제안은 한 줄로 보내고 다시 읽는다`() {
        val p = RestAreas.Proposal(munmak, "배터리 18퍼센트")
        assertEquals(p, RestAreas.Proposal.decode(p.encode()))
        val plain = RestAreas.Proposal.decode("배터리 18퍼센트")
        assertNull(plain.area)
        assertEquals("다음 휴게소", plain.placeName)
        assertNull(RestAreas.Proposal.decode("까닭\t이름\t999\t0").area)
    }
}

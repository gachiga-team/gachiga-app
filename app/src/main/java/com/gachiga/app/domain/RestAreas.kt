package com.gachiga.app.domain

import kotlin.math.abs

data class RestArea(val name: String, val lat: Double, val lon: Double)

/**
 * 함께 쉬기 제안. 순수 Kotlin.
 *
 * Pleos NaviHelper에는 장소 검색이 없어서, 앱에 넣어 둔 휴게소 목록에서 일행이 모일 곳을 직접 고른다.
 * 고른 곳은 각 차량 내비의 경유지로 넣는다.
 */
object RestAreas {

    /** "이름<TAB>위도<TAB>경도" 줄을 읽는다. #으로 시작하는 줄과 잘못된 줄은 건너뛴다. */
    fun parse(lines: Sequence<String>): List<RestArea> = lines.mapNotNull { line ->
        if (line.startsWith("#")) return@mapNotNull null
        val p = line.split('\t')
        val lat = p.getOrNull(1)?.toDoubleOrNull()
        val lon = p.getOrNull(2)?.toDoubleOrNull()
        if (p[0].isBlank() || lat == null || lon == null) null else RestArea(p[0].trim(), lat, lon)
    }.toList()

    /**
     * 모일 휴게소를 고른다.
     * - 내 진행 방향 앞쪽(±60도)에 있고 [minMeters]~[maxMeters] 떨어진 곳 가운데 가장 가까운 곳.
     * - 일행 가운데 누구도 되돌아가지 않아도 되는 곳이어야 한다 (모든 일행의 앞쪽에 있어야 한다).
     * - 진행 방향을 모르면 방향은 따지지 않고 가장 가까운 곳을 고른다.
     */
    fun pick(
        areas: List<RestArea>,
        lat: Double,
        lon: Double,
        heading: Double?,
        others: List<Pair<Double, Double>> = emptyList(),
        minMeters: Double = 2_000.0,
        maxMeters: Double = 80_000.0,
    ): RestArea? = areas
        .map { it to Geo.distanceMeters(lat, lon, it.lat, it.lon) }
        .filter { (area, d) ->
            d in minMeters..maxMeters && (heading == null || (
                within(heading, Geo.bearingDegrees(lat, lon, area.lat, area.lon), 60.0) &&
                    others.all { (oLat, oLon) ->
                        Geo.distanceMeters(oLat, oLon, area.lat, area.lon) < 1_000 ||
                            within(heading, Geo.bearingDegrees(oLat, oLon, area.lat, area.lon), 90.0)
                    }
                ))
        }
        .minByOrNull { it.second }
        ?.first

    private fun within(heading: Double, bearing: Double, degrees: Double): Boolean =
        abs(((bearing - heading) % 360.0 + 540.0) % 360.0 - 180.0) < degrees

    /** 일행에게 보내는 함께 쉬기 제안. [area]가 없으면 "다음 휴게소"라고만 한다. */
    data class Proposal(val area: RestArea?, /** "배터리 18퍼센트" 같은 까닭. 없으면 빈 문자열 */ val reason: String) {
        val placeName: String get() = area?.name ?: "다음 휴게소"

        /** 메시지 본문으로 보낼 한 줄: 까닭<TAB>이름<TAB>위도<TAB>경도 */
        fun encode(): String =
            if (area == null) reason.ifBlank { "-" }
            else listOf(reason.ifBlank { "-" }, area.name, area.lat.toString(), area.lon.toString()).joinToString("\t")

        companion object {
            fun decode(text: String): Proposal {
                val p = text.split('\t')
                val lat = p.getOrNull(2)?.toDoubleOrNull()
                val lon = p.getOrNull(3)?.toDoubleOrNull()
                val name = p.getOrNull(1)?.trim().orEmpty()
                val area = if (name.isNotEmpty() && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    RestArea(name, lat, lon)
                } else {
                    null
                }
                return Proposal(area, p[0].trim().takeIf { it != "-" }.orEmpty())
            }
        }
    }
}

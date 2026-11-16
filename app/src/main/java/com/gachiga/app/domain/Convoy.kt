package com.gachiga.app.domain

/**
 * 컨보이 모드 (같이 가기). 순수 Kotlin.
 *
 * 일행 차량의 **가장 최근 상태만** 다룬다. 위치 이력은 앱에도 서버에도 남기지 않는다 (intent.md 원칙 5).
 */
data class ConvoyMember(
    val id: String,
    /** "1호차", "2호차". 서버가 참여 순서대로 붙인다. 실명은 쓰지 않는다. */
    val label: String,
    val lat: Double? = null,
    val lon: Double? = null,
    /** 진행 방향(도). 모르면 null. */
    val heading: Double? = null,
    /** 주행 중인지. 모르면 null. */
    val moving: Boolean? = null,
    val energyPercent: Int? = null,
    val ev: Boolean = false,
    val tripKm: Double = 0.0,
    val costWon: Long = 0,
    val headcount: Int = 1,
    /** 이 차량이 여행을 끝냈는지 (합산 정산에 쓸 금액이 확정됨). */
    val done: Boolean = false,
    /** 서버가 이 차량 소식을 마지막으로 들은 뒤 지난 시간(초). 차량끼리 시계가 달라도 되도록 서버가 계산한다. */
    val ageSec: Int = 0,
) {
    val hasLocation: Boolean get() = lat != null && lon != null
}

/** 다른 차량이 나에게서 얼마나, 어느 쪽에 있는지. */
data class ConvoyGap(
    val meters: Double,
    /** 앞이면 true, 뒤면 false, 알 수 없으면 null */
    val ahead: Boolean?,
) {
    fun short(): String {
        val d = if (meters < 1000) "%.0fm".format(meters) else "%.1fkm".format(meters / 1000)
        return when (ahead) {
            true -> "$d 앞"
            false -> "$d 뒤"
            null -> d
        }
    }
}

sealed interface ConvoyAlert {
    val label: String

    data class Joined(override val label: String) : ConvoyAlert
    data class Apart(override val label: String, val gap: ConvoyGap) : ConvoyAlert
    data class Regrouped(override val label: String) : ConvoyAlert
    data class Stopped(override val label: String) : ConvoyAlert
    data class LowEnergy(override val label: String, val percent: Int, val ev: Boolean) : ConvoyAlert
    data class Lost(override val label: String) : ConvoyAlert
    data class Finished(override val label: String) : ConvoyAlert

    /** TTS로 읽을 한 문장 (주행 중에는 음성과 한 줄 정보만). */
    fun spoken(): String = when (this) {
        is Joined -> "${label}가 함께 가요."
        is Apart -> {
            val km = "%.0f킬로미터".format(gap.meters / 1000)
            when (gap.ahead) {
                true -> "${label}가 $km 앞서가고 있어요."
                false -> "${label}가 $km 뒤처졌어요."
                null -> "${label}와 $km 떨어졌어요."
            }
        }
        is Regrouped -> "${label}와 다시 가까워졌어요."
        is Stopped -> "${label}가 정차했어요."
        is LowEnergy -> "$label ${if (ev) "배터리" else "연료"}가 ${percent}퍼센트예요."
        is Lost -> "$label 위치를 받지 못하고 있어요."
        is Finished -> "${label}가 여행을 끝냈어요."
    }
}

/**
 * 일행 상태가 바뀌는 순간만 골라 알림으로 만든다. 같은 상황을 되풀이해 말하지 않는다.
 * 뒤처짐은 [apartMeters] 넘게 벌어지면 알리고, [regroupMeters] 안으로 돌아와야 다시 알릴 수 있다.
 */
class ConvoyMonitor(
    private val apartMeters: Double = 3_000.0,
    private val regroupMeters: Double = 1_000.0,
    private val lowEnergyPercent: Int = 20,
    private val lostAfterSec: Int = 90,
) {
    private class Seen(var apart: Boolean, var moving: Boolean?, var low: Boolean, var lost: Boolean, var done: Boolean)

    private val seen = HashMap<String, Seen>()
    private var first = true

    fun reset() {
        seen.clear()
        first = true
    }

    fun update(me: ConvoyMember, others: List<ConvoyMember>): List<ConvoyAlert> {
        val alerts = ArrayList<ConvoyAlert>()
        val ids = others.map { it.id }.toSet()
        seen.keys.filter { it !in ids }.forEach { seen.remove(it) }

        for (o in others) {
            val gap = gap(me, o)
            val lost = !o.done && o.ageSec >= lostAfterSec
            val low = o.energyPercent != null && o.energyPercent < lowEnergyPercent
            val prev = seen[o.id]
            if (prev == null) {
                // 처음 본 차량: 지금 상태를 기준으로 삼는다. 내가 방금 들어온 경우가 아니면 합류를 알린다.
                seen[o.id] = Seen(gap != null && gap.meters >= apartMeters, o.moving, low, lost, o.done)
                if (!first) alerts += ConvoyAlert.Joined(o.label)
                continue
            }
            if (o.done && !prev.done) alerts += ConvoyAlert.Finished(o.label)
            prev.done = o.done
            if (o.done) continue

            if (lost && !prev.lost) alerts += ConvoyAlert.Lost(o.label)
            prev.lost = lost
            if (lost) continue

            if (gap != null) {
                if (!prev.apart && gap.meters >= apartMeters) {
                    prev.apart = true
                    alerts += ConvoyAlert.Apart(o.label, gap)
                } else if (prev.apart && gap.meters <= regroupMeters) {
                    prev.apart = false
                    alerts += ConvoyAlert.Regrouped(o.label)
                }
            }
            if (prev.moving == true && o.moving == false && me.moving == true) alerts += ConvoyAlert.Stopped(o.label)
            if (o.moving != null) prev.moving = o.moving
            if (low && !prev.low) alerts += ConvoyAlert.LowEnergy(o.label, o.energyPercent!!, o.ev)
            prev.low = low
        }
        first = false
        return alerts
    }

    companion object {
        /** 나와 다른 차량 사이의 거리와 앞뒤. 둘 중 하나라도 위치를 모르면 null. */
        fun gap(me: ConvoyMember, other: ConvoyMember): ConvoyGap? {
            if (!me.hasLocation || !other.hasLocation) return null
            val meters = Geo.distanceMeters(me.lat!!, me.lon!!, other.lat!!, other.lon!!)
            val ahead = if (me.heading != null && meters >= 100) {
                Geo.isAhead(me.heading, Geo.bearingDegrees(me.lat, me.lon, other.lat, other.lon))
            } else {
                null
            }
            return ConvoyGap(meters, ahead)
        }
    }
}

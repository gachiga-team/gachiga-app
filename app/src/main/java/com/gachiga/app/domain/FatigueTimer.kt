package com.gachiga.app.domain

/**
 * 장시간 운전 휴식 안내 (spec.md 9절 N4). 순수 Kotlin.
 *
 * - 주행이 [limitMs] 동안 이어지면 안내한다. 그 뒤에도 쉬지 않으면 [repeatMs]마다 다시 안내한다.
 * - [restMs] 이상 정차하면 쉰 것으로 보고 처음부터 다시 센다. 신호 대기 같은 짧은 정차는 쉰 것으로 보지 않는다.
 * - 판별 불가(UNKNOWN)는 상태를 바꾸지 않는다.
 */
class FatigueTimer(
    private val limitMs: Long = 2 * HOUR,
    private val repeatMs: Long = HOUR,
    private val restMs: Long = 10 * MINUTE,
) {
    private var drivingSince: Long? = null
    private var parkedSince: Long? = null
    private var nextWarnAt: Long? = null

    fun onMotion(nowMs: Long, motion: MotionState) {
        when (motion) {
            MotionState.DRIVING -> {
                parkedSince = null
                if (drivingSince == null) {
                    drivingSince = nowMs
                    nextWarnAt = nowMs + limitMs
                }
            }
            MotionState.PARKED -> if (parkedSince == null) parkedSince = nowMs
            MotionState.UNKNOWN -> Unit
        }
    }

    /** @return 지금 안내해야 하면 연속 주행 시간(분), 아니면 null. */
    fun tick(nowMs: Long): Long? {
        val parked = parkedSince
        if (parked != null && nowMs - parked >= restMs) {
            drivingSince = null
            nextWarnAt = null
            return null
        }
        val since = drivingSince ?: return null
        val warnAt = nextWarnAt ?: return null
        if (parked != null || nowMs < warnAt) return null
        nextWarnAt = warnAt + repeatMs
        return (nowMs - since) / MINUTE
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** "2시간", "1시간 30분", "45분" */
        fun spoken(minutes: Long): String {
            val h = minutes / 60
            val m = minutes % 60
            return when {
                h == 0L -> "${m}분"
                m == 0L -> "${h}시간"
                else -> "${h}시간 ${m}분"
            }
        }
    }
}

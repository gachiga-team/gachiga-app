package com.gachiga.app.domain

/** 확정된 주유·충전 1건. 값은 에너지 % (spec.md F3). */
data class RefuelEvent(val percentBefore: Double, val percentAfter: Double)

/**
 * 에너지 % 변화로 사용량을 누적하고 주유·충전을 감지한다 (spec.md F2, F3). 순수 Kotlin.
 *
 * - 기준값(ref)은 내려갈 때만 따라 내려간다. 내려간 만큼이 사용량이다.
 * - 정차 중 기준값보다 오르면 주유·충전 후보로 본다. 가장 높은 값 이후 [settleMs] 동안 더 오르지 않거나,
 *   차가 출발하면 확정한다. 오른 폭이 [thresholdPercent] 이상일 때만 1건으로 기록하고 기준값을 올린다.
 * - 주행 중 상승(센서 흔들림)은 무시한다. 기준값이 그대로라 다시 내려와도 사용량으로 세지 않는다.
 */
class EnergyTracker(
    private val thresholdPercent: Double = 3.0,
    private val settleMs: Long = 30_000,
) {
    private var ref: Double? = null
    private var peak: Double? = null
    private var peakAt = 0L
    private var motion = MotionState.UNKNOWN

    /** 데이터 소스가 바뀌는 등 이전 값과 이어지지 않을 때 부른다. 다음 값이 새 기준이 된다. */
    fun reset() {
        ref = null
        peak = null
    }

    /** 정차 → 출발이면 진행 중이던 주유·충전 후보를 확정한다. */
    fun onMotion(state: MotionState): RefuelEvent? {
        val event = if (motion == MotionState.PARKED && state != MotionState.PARKED) settle() else null
        motion = state
        return event
    }

    /** @return 이번 값으로 늘어난 사용량 (%p). */
    fun onEnergy(nowMs: Long, percent: Double): Double {
        val r = ref ?: run { ref = percent; return 0.0 }
        val p = peak
        if (p != null) {
            // 주유·충전 중에는 흔들림을 사용량으로 세지 않는다.
            if (percent > p) { peak = percent; peakAt = nowMs }
            return 0.0
        }
        if (percent < r) {
            ref = percent
            return r - percent
        }
        if (percent > r && motion == MotionState.PARKED) {
            peak = percent
            peakAt = nowMs
        }
        return 0.0
    }

    /** 주기적으로 불러서, 오르기를 멈춘 지 [settleMs]가 지난 후보를 확정한다. */
    fun tick(nowMs: Long): RefuelEvent? =
        if (peak != null && nowMs - peakAt >= settleMs) settle() else null

    private fun settle(): RefuelEvent? {
        val p = peak ?: return null
        val r = ref ?: return null
        peak = null
        if (p - r < thresholdPercent) return null
        ref = p
        return RefuelEvent(percentBefore = r, percentAfter = p)
    }
}

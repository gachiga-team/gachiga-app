package com.gachiga.app.domain

/**
 * 기어를 읽을 수 없는 차량에서 주행거리 변화로 주행/정차를 추정한다 (spec.md 3.2-2).
 *
 * - 최근 [drivingWindowMs] 사이에 주행거리가 [drivingDeltaKm] 넘게 늘면 DRIVING
 * - [parkedAfterMs] 동안 주행거리 변화가 없으면 PARKED
 * - 그 사이는 직전 상태를 유지한다. 처음에는 UNKNOWN.
 *
 * 주행거리는 값이 바뀔 때만 들어오는 계단 함수로 본다. 순수 Kotlin이라 단위 테스트한다.
 */
class OdometerMotionEstimator(
    private val drivingWindowMs: Long = 20_000,
    private val drivingDeltaKm: Double = 0.05,
    private val parkedAfterMs: Long = 60_000,
) {
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var lastKm: Double? = null
    private var lastChangeAt: Long? = null

    var state: MotionState = MotionState.UNKNOWN
        private set

    fun onOdometer(nowMs: Long, km: Double): MotionState {
        if (lastKm == null || km != lastKm) lastChangeAt = nowMs
        lastKm = km
        samples.addLast(nowMs to km)
        return evaluate(nowMs)
    }

    /** 주행거리 값이 오지 않아도 주기적으로 불러서 '정차' 판정을 갱신한다. */
    fun evaluate(nowMs: Long): MotionState {
        val latest = lastKm ?: return state
        val windowStart = nowMs - drivingWindowMs
        // 창 시작 시점의 값을 알 수 있도록, 창 밖 샘플은 마지막 하나만 남긴다.
        while (samples.size >= 2 && samples[1].first <= windowStart) samples.removeFirst()
        val kmAtWindowStart = samples.first().second

        state = when {
            latest - kmAtWindowStart > drivingDeltaKm -> MotionState.DRIVING
            nowMs - (lastChangeAt ?: nowMs) >= parkedAfterMs -> MotionState.PARKED
            else -> state
        }
        return state
    }
}

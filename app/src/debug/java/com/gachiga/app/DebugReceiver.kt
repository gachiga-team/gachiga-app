package com.gachiga.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * debug 빌드 전용: 시연 영상 녹화 스크립트가 adb로 내비 테스트 경로를 건다.
 * release에는 이 클래스와 receiver 선언이 들어가지 않는다 (src/debug).
 *
 * adb shell am broadcast --user 10 -n com.gachiga.app/.DebugReceiver -a com.gachiga.app.DEBUG_TEST_ROUTE
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TEST_ROUTE) return
        Log.i("DebugReceiver", "test route requested")
        (context.applicationContext as GachiGaApp).container.navi.requestTestRoute(37.7639, 128.8994, "강릉역")
    }

    companion object {
        const val ACTION_TEST_ROUTE = "com.gachiga.app.DEBUG_TEST_ROUTE"
    }
}

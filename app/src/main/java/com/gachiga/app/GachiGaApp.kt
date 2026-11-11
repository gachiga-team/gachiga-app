package com.gachiga.app

import android.app.Application

class GachiGaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.recorder.attach() // 진행 중인 여행이 있으면 기록을 이어 간다 (F1)
        container.voice // 음성 SDK는 초기화가 비동기라 미리 시작해 둔다 (F8)
        container.navi  // 내비 연동도 미리 연결해 둔다 (N1~N3)
    }
}

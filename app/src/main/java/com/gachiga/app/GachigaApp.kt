package com.gachiga.app

import android.app.Application

class GachigaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.recorder.attach() // 진행 중인 여행이 있으면 기록을 이어 간다 (F1)
    }
}

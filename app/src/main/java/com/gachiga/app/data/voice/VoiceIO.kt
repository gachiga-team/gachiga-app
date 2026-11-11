package com.gachiga.app.data.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 음성 입출력 (spec.md F8). STT·TTS를 ON_DEVICE 모드로 시도하고, 안 되면 터치로 대체한다.
 * 음성이 없어도 모든 기능은 터치로 쓸 수 있어야 한다.
 */
interface VoiceIO {
    /** 음성 인식을 쓸 수 있는지. false면 음성 버튼을 숨긴다. */
    val canListen: StateFlow<Boolean>

    /** 음성 안내를 쓸 수 있는지. */
    val canSpeak: StateFlow<Boolean>

    /** 한 번 듣고 인식된 문장을 돌려준다. 인식 준비 후 [timeoutMs] 안에 말이 없거나 실패하면 null. */
    suspend fun listen(timeoutMs: Long = 10_000): String?

    /** 다 읽을 때까지 기다린다. 안내 음성을 다시 인식하지 않도록 듣기 전에 끝나야 한다. */
    suspend fun speak(text: String)

    fun release()
}

/** 음성 미지원 시. 음성 버튼을 숨기고 터치 UI로 대체한다. */
object NoopVoiceIO : VoiceIO {
    override val canListen: StateFlow<Boolean> = MutableStateFlow(false)
    override val canSpeak: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun listen(timeoutMs: Long): String? = null
    override suspend fun speak(text: String) = Unit
    override fun release() = Unit
}

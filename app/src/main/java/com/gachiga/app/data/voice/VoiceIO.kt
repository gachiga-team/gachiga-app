package com.gachiga.app.data.voice

/**
 * 음성 입출력 (spec.md F8). STT·TTS를 ON_DEVICE 모드로 시도하고, 안 되면 NoopVoiceIO로 바꾼다.
 * 음성이 없어도 모든 기능은 터치로 쓸 수 있어야 한다.
 */
interface VoiceIO {
    val isAvailable: Boolean

    /** 한 번 듣고 인식된 텍스트를 돌려준다. 실패하거나 시간이 지나면 null. */
    suspend fun listen(): String?

    fun speak(text: String)

    fun release()
}

/** 음성 미지원 시. 음성 버튼을 숨기고 터치 UI로 대체한다. */
object NoopVoiceIO : VoiceIO {
    override val isAvailable = false
    override suspend fun listen(): String? = null
    override fun speak(text: String) = Unit
    override fun release() = Unit
}

// TODO(V4): PleosVoiceIO — SpeechToText(context, Mode.ON_DEVICE), TextToSpeech(context, Mode.ON_DEVICE), ko-KR.
//  Gradle 좌표와 권한(RECORD_AUDIO)을 확인한 뒤 추가한다.

package com.gachiga.app.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * Pleos SpeechToText·TextToSpeech 3.3.1, ON_DEVICE 모드 (spec.md F8).
 *
 * ON_DEVICE는 앱 등록(Client ID/Secret)이 필요 없다. 실제 녹음과 합성은 차량의 Pleos 음성 서비스
 * (ai.pleos.playground.caas)가 하므로 앱은 마이크 권한을 직접 요청하지 않는다.
 * 초기화에 실패하면 canListen/canSpeak가 false로 남고, 화면은 터치로 대체한다.
 */
class PleosVoiceIO(context: Context) : VoiceIO {

    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)

    /**
     * 음성 안내가 나올 때 미디어 볼륨을 낮춘다 (UI_GUIDELINES.md 4절: 알림은 음성으로).
     * 안내 음성 용도로 표시하고, 말하는 동안만 오디오 포커스를 MAY_DUCK으로 잡는다.
     */
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val duckRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()

    private val _canListen = MutableStateFlow(false)
    private val _canSpeak = MutableStateFlow(false)
    override val canListen: StateFlow<Boolean> = _canListen.asStateFlow()
    override val canSpeak: StateFlow<Boolean> = _canSpeak.asStateFlow()

    fun initialize() {
        runCatching {
            stt.initialize(object : SttInitListener {
                override fun onSuccess() {
                    runCatching { stt.setLocale(Locale.KOREA) }
                    Log.i(TAG, "STT initialized")
                    _canListen.value = true
                }
                override fun onFailure(error: SttError) {
                    Log.w(TAG, "STT initialize failed: $error")
                }
            })
        }.onFailure { Log.w(TAG, "STT initialize threw", it) }

        runCatching {
            tts.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    runCatching { tts.setLocale(Locale.KOREA) }
                    runCatching { tts.setAudioAttributes(speechAttributes) }
                    Log.i(TAG, "TTS initialized")
                    _canSpeak.value = true
                }
                override fun onFailure(error: TtsError) {
                    Log.w(TAG, "TTS initialize failed: $error")
                }
            })
        }.onFailure { Log.w(TAG, "TTS initialize threw", it) }
    }

    /**
     * 에뮬레이터에서 확인한 순서 (2026-11-11): onReady → (말함) → onEndedRecognition → onUpdated(최종 문장, true).
     * 말 끝남이 최종 문장보다 먼저 오므로, 끝남 뒤에도 [FINAL_GRACE_MS] 동안 최종 문장을 기다린다.
     * 첫 사용 때는 인식 엔진 준비(onReady)에 8초 넘게 걸릴 수 있어서 전체 상한은 넉넉히 두고,
     * 준비된 뒤부터 [timeoutMs] 동안 듣는다.
     */
    override suspend fun listen(timeoutMs: Long): String? {
        if (!_canListen.value) return null
        val handler = Handler(Looper.getMainLooper())
        return withTimeoutOrNull(READY_TIMEOUT_MS + timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                var lastPartial: String? = null
                lateinit var listener: ResultListener
                fun finish(text: String?) {
                    if (done.compareAndSet(false, true)) {
                        handler.removeCallbacksAndMessages(null)
                        runCatching { stt.stop() }
                        stt.removeListener(listener)
                        Log.i(TAG, "listen result: $text")
                        if (cont.isActive) cont.resume(text?.takeIf { it.isNotBlank() })
                    }
                }
                listener = object : ResultListener {
                    override fun onUpdated(text: String, isFinal: Boolean) {
                        Log.d(TAG, "stt update final=$isFinal: $text")
                        if (text.isNotBlank()) lastPartial = text
                        if (isFinal) finish(text)
                    }
                    override fun onUpdatedEpdData(start: Long, end: Long) = Unit
                    override fun onStartedRecognition() { Log.d(TAG, "stt started") }
                    override fun onEndedRecognition() {
                        Log.d(TAG, "stt ended, waiting for final result")
                        handler.postDelayed({ finish(lastPartial) }, FINAL_GRACE_MS)
                    }
                    override fun onError(error: SttError) {
                        Log.w(TAG, "stt error: $error")
                        finish(null)
                    }
                    override fun onReady() {
                        Log.d(TAG, "stt ready")
                        handler.postDelayed({ finish(lastPartial) }, timeoutMs)
                    }
                    override fun onUpdatedRms(rms: Double) = Unit
                }
                stt.addListener(listener)
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) {
                        handler.removeCallbacksAndMessages(null)
                        runCatching { stt.stop() }
                        stt.removeListener(listener)
                    }
                }
                runCatching { stt.request() }.onFailure {
                    Log.w(TAG, "stt request failed", it)
                    finish(null)
                }
            }
        }
    }

    private suspend fun speakAndWait(text: String) {
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                lateinit var listener: EventListener
                fun finish() {
                    if (done.compareAndSet(false, true)) {
                        tts.removeEventListener(listener)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                listener = object : EventListener {
                    override fun onStart() = Unit
                    override fun onReady() = Unit
                    override fun onDone() = finish()
                    override fun onError(error: TtsError) {
                        Log.w(TAG, "tts error: $error")
                        finish()
                    }
                    override fun onStop() = finish()
                    override fun onUpdatedRms(rms: Double) = Unit
                }
                tts.addEventListener(listener)
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) tts.removeEventListener(listener)
                }
                Log.i(TAG, "speak: $text")
                runCatching { tts.speak(text, true) }.onFailure {
                    Log.w(TAG, "tts speak failed", it)
                    finish()
                }
            }
        }
    }

    override suspend fun speak(text: String) {
        if (!_canSpeak.value) return
        val focus = runCatching { audioManager?.requestAudioFocus(duckRequest) }.getOrNull()
        Log.d(TAG, "audio focus (duck) request: $focus")
        try {
            speakAndWait(text)
        } finally {
            runCatching { audioManager?.abandonAudioFocusRequest(duckRequest) }
        }
    }

    override fun release() {
        runCatching { stt.release() }
        runCatching { tts.release() }
    }

    private companion object {
        const val TAG = "PleosVoice"
        const val SPEAK_TIMEOUT_MS = 15_000L
        const val READY_TIMEOUT_MS = 12_000L
        const val FINAL_GRACE_MS = 2_500L
    }
}

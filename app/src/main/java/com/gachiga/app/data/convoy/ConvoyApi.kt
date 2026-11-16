package com.gachiga.app.data.convoy

import com.gachiga.app.domain.ConvoyMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 그룹에 들어갈 때 받는 표. [key]는 이 차량만 아는 값이라 다른 차량에 보여 주지 않는다. */
data class ConvoyTicket(val code: String, val key: String, val id: String, val label: String)

data class ConvoyMessage(
    val seq: Long,
    val fromId: String,
    /** "2호차" */
    val from: String,
    /** voice: 음성 메시지, rest: 함께 쉬기 제안, left: 그룹에서 나감 */
    val kind: String,
    val text: String,
)

data class ConvoySnapshot(val members: List<ConvoyMember>, val messages: List<ConvoyMessage>, val seq: Long)

/** 내 차량이 서버에 올리는 최신 상태. 위치는 여행 중에만 넣는다. */
data class ConvoyUpload(
    val lat: Double?,
    val lon: Double?,
    val heading: Double?,
    val moving: Boolean?,
    val energyPercent: Int?,
    val ev: Boolean,
    val tripKm: Double,
    val costWon: Long,
    val headcount: Int,
    val done: Boolean,
)

class ConvoyException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { NO_ROOM, FULL, NOT_MEMBER, NETWORK }
}

/**
 * 컨보이 중계 서버(server/convoy-server.mjs)와 주고받는다.
 * 의존성을 늘리지 않으려고 HttpURLConnection과 org.json을 쓴다.
 */
class ConvoyApi(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    suspend fun create(): ConvoyTicket = ticket(post("/rooms", JSONObject()))

    suspend fun join(code: String): ConvoyTicket = ticket(post("/rooms/$code/join", JSONObject()))

    suspend fun sync(ticket: ConvoyTicket, upload: ConvoyUpload, afterSeq: Long): ConvoySnapshot {
        val state = JSONObject()
            .put("lat", upload.lat ?: JSONObject.NULL)
            .put("lon", upload.lon ?: JSONObject.NULL)
            .put("heading", upload.heading ?: JSONObject.NULL)
            .put("moving", upload.moving ?: JSONObject.NULL)
            .put("energy", upload.energyPercent ?: JSONObject.NULL)
            .put("ev", upload.ev)
            .put("km", upload.tripKm)
            .put("cost", upload.costWon)
            .put("headcount", upload.headcount)
            .put("done", upload.done)
        val res = post("/rooms/${ticket.code}/sync", JSONObject().put("key", ticket.key).put("state", state).put("after", afterSeq))
        val members = res.getJSONArray("members").let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                ConvoyMember(
                    id = m.getString("id"),
                    label = m.getString("label"),
                    lat = m.doubleOrNull("lat"),
                    lon = m.doubleOrNull("lon"),
                    heading = m.doubleOrNull("heading"),
                    moving = if (m.isNull("moving")) null else m.optBoolean("moving"),
                    energyPercent = m.doubleOrNull("energy")?.toInt(),
                    ev = m.optBoolean("ev"),
                    tripKm = m.optDouble("km", 0.0),
                    costWon = m.optLong("cost", 0),
                    headcount = m.optInt("headcount", 1),
                    done = m.optBoolean("done"),
                    ageSec = m.optInt("age", 0),
                )
            }
        }
        val messages = res.getJSONArray("messages").let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                ConvoyMessage(m.getLong("seq"), m.getString("fromId"), m.getString("from"), m.getString("kind"), m.optString("text"))
            }
        }
        return ConvoySnapshot(members, messages, res.optLong("seq", afterSeq))
    }

    suspend fun send(ticket: ConvoyTicket, kind: String, text: String) {
        post("/rooms/${ticket.code}/messages", JSONObject().put("key", ticket.key).put("kind", kind).put("text", text))
    }

    suspend fun leave(ticket: ConvoyTicket) {
        post("/rooms/${ticket.code}/leave", JSONObject().put("key", ticket.key))
    }

    private fun ticket(o: JSONObject) = ConvoyTicket(o.getString("code"), o.getString("key"), o.getString("id"), o.getString("label"))

    private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        } catch (e: Exception) {
            throw ConvoyException(ConvoyException.Reason.NETWORK)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                throw ConvoyException(
                    when (error) {
                        "no_room" -> ConvoyException.Reason.NO_ROOM
                        "full" -> ConvoyException.Reason.FULL
                        "not_member" -> ConvoyException.Reason.NOT_MEMBER
                        else -> ConvoyException.Reason.NETWORK
                    },
                )
            }
            JSONObject(text)
        } catch (e: ConvoyException) {
            throw e
        } catch (e: Exception) {
            throw ConvoyException(ConvoyException.Reason.NETWORK)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}

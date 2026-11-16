package com.gachiga.app.domain

import java.util.Base64

/**
 * QR 정산서에 담을 내용 (spec.md 9절 N5). 순수 Kotlin.
 *
 * 서버를 두지 않는다. 정산 내용을 JSON → UTF-8 → base64url로 바꿔 웹 페이지 주소의 `#` 뒤에 붙인다.
 * `#` 뒤는 브라우저가 서버로 보내지 않으므로, 정적 페이지를 호스팅하는 곳에도 정산 내용이 남지 않는다.
 * 위치나 경로 같은 민감한 정보는 담지 않는다 (intent.md 원칙 5).
 */
object SettlementShare {

    data class Sheet(
        val title: String,             // "강릉역 여행"
        val period: String,            // "11월 10일 20:27 ~ 20:31"
        val headcount: Int,
        val distanceKm: Double,
        val energyLabel: String,       // "배터리" / "연료"
        val energyUsed: Double,
        val unit: String,              // "kWh" / "L"
        val unitPrice: Double,
        val priceBasis: String,        // "충전 금액으로 계산" / "기본 단가로 추정"
        val energyCostWon: Long,
        val otherCostWon: Long,
        val totalCostWon: Long,
        val perPersonWon: Long,
        val driverShareWon: Long,
        val items: List<Pair<String, Long>>, // 기타 비용 내역 ("통행료 (내비 예상)", 6860)
        /** 같이 가기로 여러 차량이 함께 간 여행이면 그룹 합산 정산을 함께 담는다. */
        val group: Group? = null,
    )

    /** 여러 차량 합산 정산. 차량은 "1호차"처럼 부르고 실명은 담지 않는다. */
    data class Group(
        val code: String,
        val myLabel: String,
        val totalCostWon: Long,
        val headcount: Int,
        val perPersonWon: Long,
        /** 아직 여행을 끝내지 않은 차량이 있어 금액이 바뀔 수 있는지 */
        val pending: Boolean,
        val cars: List<Car>,
        val transfers: List<Transfer>,
    )

    data class Car(val label: String, val headcount: Int, val costWon: Long)
    data class Transfer(val from: String, val to: String, val amountWon: Long)

    /** 웹 정산서 주소. [baseUrl]이 비어 있으면 null (그때는 [plainText]를 쓴다). */
    fun url(baseUrl: String, sheet: Sheet): String? {
        if (baseUrl.isBlank()) return null
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json(sheet).toByteArray(Charsets.UTF_8))
        return "${baseUrl.trimEnd('#')}#$encoded"
    }

    /** 웹 주소가 없을 때 QR에 담는 글자 정산서. 폰 카메라로 찍으면 글로 보인다. */
    fun plainText(sheet: Sheet): String = buildString {
        appendLine("[같이가] ${sheet.title} 정산")
        appendLine(sheet.period)
        sheet.group?.let { g ->
            appendLine("그룹 ${g.code} 합산: 총 %,d원 / %d명 = 1인 %,d원".format(g.totalCostWon, g.headcount, g.perPersonWon))
            appendLine("동승자는 자기 차 운전자에게 %,d원".format(g.perPersonWon))
            g.transfers.forEach { appendLine("${it.from} 운전자 → ${it.to} 운전자 %,d원".format(it.amountWon)) }
            if (g.pending) appendLine("(아직 달리는 차량이 있어 금액이 바뀔 수 있어요)")
            appendLine("--- ${g.myLabel} 내역 ---")
        }
        if (sheet.group == null && sheet.headcount > 1) appendLine("동승자 1인당 운전자에게 %,d원".format(sheet.perPersonWon))
        appendLine("총비용 %,d원 (%d명)".format(sheet.totalCostWon, sheet.headcount))
        appendLine("- 거리 %.1fkm (차량 측정)".format(sheet.distanceKm))
        appendLine("- %s %.1f%s × %,.0f원 = %,d원 (%s)".format(sheet.energyLabel, sheet.energyUsed, sheet.unit, sheet.unitPrice, sheet.energyCostWon, sheet.priceBasis))
        sheet.items.forEach { (label, amount) -> appendLine("- %s %,d원".format(label, amount)) }
        if (sheet.group == null && sheet.headcount > 1) append("운전자 부담 %,d원".format(sheet.driverShareWon))
    }.trimEnd()

    /** 키를 짧게 써서 QR을 작게 만든다. 웹 페이지(web/settle/index.html)와 같은 키를 쓴다. */
    fun json(s: Sheet): String = buildString {
        append('{')
        append("\"v\":1,")
        append("\"t\":").append(q(s.title)).append(',')
        append("\"d\":").append(q(s.period)).append(',')
        append("\"n\":").append(s.headcount).append(',')
        append("\"km\":").append(num(s.distanceKm)).append(',')
        append("\"el\":").append(q(s.energyLabel)).append(',')
        append("\"eu\":").append(num(s.energyUsed)).append(',')
        append("\"u\":").append(q(s.unit)).append(',')
        append("\"up\":").append(s.unitPrice.toLong()).append(',')
        append("\"pb\":").append(q(s.priceBasis)).append(',')
        append("\"ec\":").append(s.energyCostWon).append(',')
        append("\"oc\":").append(s.otherCostWon).append(',')
        append("\"tc\":").append(s.totalCostWon).append(',')
        append("\"pp\":").append(s.perPersonWon).append(',')
        append("\"ds\":").append(s.driverShareWon).append(',')
        s.group?.let { g ->
            append("\"g\":{")
            append("\"c\":").append(q(g.code)).append(',')
            append("\"me\":").append(q(g.myLabel)).append(',')
            append("\"tc\":").append(g.totalCostWon).append(',')
            append("\"n\":").append(g.headcount).append(',')
            append("\"pp\":").append(g.perPersonWon).append(',')
            append("\"p\":").append(if (g.pending) 1 else 0).append(',')
            append("\"cars\":[")
            g.cars.forEachIndexed { i, c ->
                if (i > 0) append(',')
                append('[').append(q(c.label)).append(',').append(c.headcount).append(',').append(c.costWon).append(']')
            }
            append("],\"tr\":[")
            g.transfers.forEachIndexed { i, t ->
                if (i > 0) append(',')
                append('[').append(q(t.from)).append(',').append(q(t.to)).append(',').append(t.amountWon).append(']')
            }
            append("]},")
        }
        append("\"x\":[")
        s.items.forEachIndexed { i, (label, amount) ->
            if (i > 0) append(',')
            append('[').append(q(label)).append(',').append(amount).append(']')
        }
        append("]}")
    }

    private fun num(v: Double): String = "%.1f".format(java.util.Locale.ROOT, v)

    private fun q(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

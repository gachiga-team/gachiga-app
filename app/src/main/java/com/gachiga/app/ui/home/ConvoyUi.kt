package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gachiga.app.data.convoy.ConvoyMessage
import com.gachiga.app.data.convoy.ConvoyState
import com.gachiga.app.domain.ConvoyMember
import com.gachiga.app.domain.ConvoyMonitor
import com.gachiga.app.domain.GroupSettlement
import com.gachiga.app.domain.RestAreas
import com.gachiga.app.domain.SettlementShare
import com.gachiga.app.ui.theme.CarDimens
import com.gachiga.app.ui.theme.SubtleText

/**
 * 같이 가기(컨보이) 영역. 정차 중 여행 화면에만 보인다.
 * 주행 중에는 [convoyDrivingLine] 한 줄과 음성 알림만 쓴다 (UI_GUIDELINES.md 5절).
 */
@Composable
fun ConvoyPanel(convoy: ConvoyState?, canListen: Boolean, listening: Boolean, vm: HomeViewModel) {
    HorizontalDivider()
    if (convoy == null) {
        Text("같이 가기", style = MaterialTheme.typography.headlineMedium)
        Text("일행 차량과 위치를 나누고, 끝나면 함께 정산해요.", style = MaterialTheme.typography.bodyMedium, color = SubtleText)
        ButtonRow {
            SecondaryButton("그룹 만들기", vm::onGroupCreateClick)
            SecondaryButton("그룹 참여", vm::onGroupJoinClick)
        }
        return
    }
    Text("같이 가기 · 그룹 ${convoy.code} · ${convoy.myLabel}", style = MaterialTheme.typography.headlineMedium)
    if (!convoy.online) {
        Text("일행 서버에 다시 연결하는 중이에요…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
    if (convoy.others.isEmpty()) {
        Text("아직 다른 차량이 없어요. 일행에게 그룹 번호를 알려 주세요.", style = MaterialTheme.typography.bodyLarge)
    }
    convoy.others.forEach { Text(memberLine(convoy.me, it), style = MaterialTheme.typography.bodyLarge) }
    convoy.lastMessage?.let { Text(messageLine(it), style = MaterialTheme.typography.bodyMedium, color = SubtleText) }
    Text(
        if (convoy.sharingLocation) "내 위치를 일행과 나누는 중이에요. 여행을 끝내면 멈춰요." else "지금은 내 위치를 나누지 않고 있어요.",
        style = MaterialTheme.typography.bodyMedium,
        color = SubtleText,
    )
    ButtonRow {
        if (!convoy.sharingLocation && !convoy.done && vm.needsLocationPermission()) {
            SecondaryButton("위치 나누기 켜기", vm::onShareLocationClick)
        }
        if (canListen) SecondaryButton("일행에게 말하기", vm::onGroupTalkClick, enabled = !listening)
        if (!convoy.done && convoy.others.any { !it.done }) SecondaryButton("함께 쉬기 제안", vm::onRestProposeClick)
        SecondaryButton("그룹 번호", vm::onGroupCodeClick)
        SecondaryButton("그룹 나가기", vm::onGroupLeaveClick)
    }
}

/** 일행 차량 한 줄: "2호차 · 1.2km 뒤 · 주행 중 · 배터리 45%" */
fun memberLine(me: ConvoyMember?, o: ConvoyMember): String {
    if (o.done) return "${o.label} · 여행 끝 · ${won(o.costWon)}"
    val parts = mutableListOf(o.label)
    if (o.ageSec >= 90) {
        parts += "소식 없음"
        return parts.joinToString(" · ")
    }
    val gap = me?.let { ConvoyMonitor.gap(it, o) }
    parts += gap?.short() ?: "위치 확인 중"
    when (o.moving) {
        true -> parts += "주행 중"
        false -> parts += "정차 중"
        null -> Unit
    }
    o.energyPercent?.let { parts += "${if (o.ev) "배터리" else "연료"} $it%" }
    return parts.joinToString(" · ")
}

private fun messageLine(m: ConvoyMessage): String = when (m.kind) {
    "rest" -> "${m.from}: ${RestAreas.Proposal.decode(m.text).placeName}에서 함께 쉬어요"
    "rest-ok" -> "${m.from}: 함께 쉬어요"
    else -> "${m.from}: ${m.text}"
}

/** 주행 중 화면에 보여 줄 한 줄. 가까운 일행 두 대까지. 없으면 null. */
fun convoyDrivingLine(convoy: ConvoyState?): String? {
    val me = convoy?.me ?: return null
    val lines = convoy.others.filter { !it.done }
        .mapNotNull { o -> ConvoyMonitor.gap(me, o)?.let { o.label to it } }
        .sortedBy { it.second.meters }
        .take(2)
        .map { (label, gap) -> "$label ${gap.short()}" }
    return lines.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** 내 차와 일행 차량의 비용을 합쳐 나눈다. 일행 금액은 각 차량이 올린 최근 값이다. */
fun groupSettlement(convoy: ConvoyState, myCostWon: Long, myHeadcount: Int): GroupSettlement.Result {
    val cars = convoy.others.map { GroupSettlement.Car(it.label, it.costWon, it.headcount) } +
        GroupSettlement.Car(convoy.myLabel, myCostWon, myHeadcount)
    return GroupSettlement.calculate(cars.sortedBy { c -> c.label.filter { it.isDigit() }.toIntOrNull() ?: 0 })
}

/** QR 정산서에 담을 그룹 합산 내용. */
fun groupShare(convoy: ConvoyState, myCostWon: Long, myHeadcount: Int): SettlementShare.Group {
    val g = groupSettlement(convoy, myCostWon, myHeadcount)
    return SettlementShare.Group(
        code = convoy.code,
        myLabel = convoy.myLabel,
        totalCostWon = g.totalCostWon,
        headcount = g.headcount,
        perPersonWon = g.perPersonWon,
        pending = convoy.others.any { !it.done },
        cars = g.cars.map { SettlementShare.Car(it.label, it.headcount, it.costWon) },
        transfers = g.transfers.map { SettlementShare.Transfer(it.from, it.to, it.amountWon) },
    )
}

/** 정산 화면의 그룹 합산 카드: 누가 누구에게 얼마. */
@Composable
fun GroupSettlementCard(convoy: ConvoyState, myCostWon: Long, myHeadcount: Int) {
    val g = groupSettlement(convoy, myCostWon, myHeadcount)
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(CarDimens.gap), verticalArrangement = Arrangement.spacedBy(CarDimens.gap / 2)) {
            Text("그룹 ${convoy.code} 합산 정산 · ${g.cars.size}대 ${g.headcount}명", style = MaterialTheme.typography.headlineMedium)
            Text(
                "동승자는 각자 자기 차 운전자에게 ${won(g.perPersonWon)}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (g.transfers.isEmpty()) Text("운전자끼리 주고받을 금액은 없어요.", style = MaterialTheme.typography.bodyLarge)
            g.transfers.forEach {
                Text("${it.from} 운전자 → ${it.to} 운전자 ${won(it.amountWon)}", style = MaterialTheme.typography.bodyLarge)
            }
            g.cars.forEach {
                Text(
                    "${it.label}${if (it.label == convoy.myLabel) " (이 차)" else ""} · ${it.headcount}명 · ${won(it.costWon)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (convoy.others.any { !it.done }) {
                Text("아직 달리는 차량이 있어 금액이 바뀔 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Text(
                "전체 ${won(g.totalCostWon)} ÷ ${g.headcount}명. 10원 미만 나머지는 비용이 가장 큰 차량이 부담해요.",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleText,
            )
        }
    }
}

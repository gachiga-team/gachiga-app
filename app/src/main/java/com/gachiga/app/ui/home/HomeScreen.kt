package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachiga.app.domain.MotionState
import com.gachiga.app.ui.theme.CarDimens

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // F7: 주행 중(또는 판별 불가)이면 한 줄 정보만 보여 주고 입력 UI를 없앤다.
        // 단, 차량 값을 하나도 읽지 못하면 기록할 것이 없으므로 잠그지 않고 데모 모드를 안내한다 (spec.md 3.2-4).
        if (state.motion.locksInput && state.capabilities.canRecordTrip) {
            DrivingModeScreen(state, onDemoPark = vm::demoPark)
        } else {
            ParkedScreen(state, vm)
        }
    }
}

@Composable
private fun DrivingModeScreen(state: HomeUiState, onDemoPark: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.demoMode) DemoBadge()
            Text(summaryLine(state), style = MaterialTheme.typography.displayMedium)
            // 데모 모드에서만: 차량 동작(정차)을 흉내 내는 버튼. 실제 차량에서는 보이지 않는다.
            if (state.demoMode) {
                OutlinedButton(onClick = onDemoPark, Modifier.height(CarDimens.buttonHeight)) { Text("정차 (데모)") }
            }
        }
    }
}

@Composable
private fun ParkedScreen(state: HomeUiState, vm: HomeViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.demoMode) DemoBadge()
        Text("같이가", style = MaterialTheme.typography.headlineMedium)
        Text(summaryLine(state), style = MaterialTheme.typography.bodyLarge)

        if (!state.capabilities.canRecordTrip) {
            Text(
                "이 차량에서 주행거리나 연료·배터리 값을 읽지 못했어요. 데모 모드로 확인할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedButton(
            onClick = { vm.setDemoMode(!state.demoMode) },
            modifier = Modifier.height(CarDimens.buttonHeight),
        ) { Text(if (state.demoMode) "데모 모드 끄기" else "데모 모드 켜기") }

        if (state.demoMode) {
            // F10 데모 패널
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = vm::demoDrive, Modifier.height(CarDimens.buttonHeight)) { Text("10 km 주행") }
                Button(onClick = { vm.demoAddEnergy(50.0) }, Modifier.height(CarDimens.buttonHeight)) { Text("주유 +50%") }
                Button(onClick = { vm.demoAddEnergy(40.0) }, Modifier.height(CarDimens.buttonHeight)) { Text("충전 +40%") }
            }
        }
    }
}

@Composable
private fun DemoBadge() {
    Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
        Text("데모 모드", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private fun summaryLine(state: HomeUiState): String {
    val odo = state.odometerKm?.let { "%,.1f km".format(it) } ?: "주행거리 -"
    val energy = state.energyPercent?.let { "에너지 %.0f%%".format(it) } ?: "에너지 -"
    val motion = when (state.motion) {
        MotionState.DRIVING -> "주행 중"
        MotionState.PARKED -> "정차 중"
        MotionState.UNKNOWN -> "상태 확인 중"
    }
    return "$motion · $odo · $energy"
}

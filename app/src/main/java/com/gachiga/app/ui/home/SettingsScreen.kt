package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gachiga.app.data.settings.VehicleSettings
import com.gachiga.app.data.vehicle.VehicleCapabilities
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.SettingsInput
import com.gachiga.app.ui.theme.CarDimens
import com.gachiga.app.ui.theme.SubtleText

/**
 * 차량 설정 (spec.md F9). 정차 중에만 열린다.
 * - 차량이 연료·배터리 중 하나만 지원하면 종류를 고정한다 (capability 확인 결과).
 * - 바꾼 설정은 다음 여행부터 적용된다 (진행 중인 여행은 시작 때 값을 복사해 둠).
 */
@Composable
fun SettingsScreen(
    settings: VehicleSettings,
    capabilities: VehicleCapabilities,
    firstRun: Boolean,
    tripInProgress: Boolean,
    demoMode: Boolean,
    onToggleDemo: () -> Unit,
    onSave: (VehicleSettings) -> Unit,
    onCancel: () -> Unit,
) {
    val detected: EnergyType? = when {
        capabilities.evSoc && !capabilities.fuelPercent -> EnergyType.EV
        capabilities.fuelPercent && !capabilities.evSoc -> EnergyType.FUEL
        else -> null
    }
    var type by remember(detected) { mutableStateOf(detected ?: settings.energyType) }
    var capacityText by remember(type) {
        mutableStateOf(trimZero(if (type == EnergyType.EV) settings.evCapacityKWh else settings.fuelCapacityL))
    }
    var priceText by remember(type) {
        mutableStateOf((if (type == EnergyType.EV) settings.evPriceWonPerKWh else settings.fuelPriceWonPerL).toString())
    }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(CarDimens.screenPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
    ) {
        Text("차량 설정", style = MaterialTheme.typography.headlineMedium)
        if (firstRun) {
            Text(
                "정산에 쓰는 값이에요. 차량 등록증이나 제조사 사양에서 확인해 넣어 주세요. 나중에 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        ButtonRow {
            EnergyType.entries.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t; error = null },
                    enabled = detected == null,
                    label = { Text(if (t == EnergyType.EV) "전기차" else "내연기관", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = CarDimens.touch),
                )
            }
        }
        if (detected != null) {
            Text(
                "이 차량은 ${if (detected == EnergyType.EV) "전기차" else "내연기관 차량"}로 확인됐어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleText,
            )
        }

        ButtonRow {
            NumberField(
                label = if (type == EnergyType.EV) "배터리 용량 (kWh)" else "연료 탱크 용량 (L)",
                value = capacityText,
                decimal = true,
                onChange = { capacityText = it; error = null },
            )
            NumberField(
                label = if (type == EnergyType.EV) "기본 충전 단가 (원/kWh)" else "기본 유가 (원/L)",
                value = priceText,
                decimal = false,
                onChange = { priceText = it; error = null },
            )
        }
        Text(
            "기본 단가는 금액을 입력하지 않은 ${type.refuelWord()}에만 쓰는 추정값이에요. " +
                "${type.refuelWord()} 금액을 입력하면 그 금액으로 단가를 다시 계산해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = SubtleText,
        )
        if (tripInProgress) {
            Text("진행 중인 여행에는 적용되지 않고, 다음 여행부터 적용돼요.", style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }

        ButtonRow {
            Button(
                onClick = {
                    val cap = SettingsInput.capacity(capacityText, type)
                    val price = SettingsInput.price(priceText, type)
                    when {
                        cap is SettingsInput.Result.Error -> error = cap.message
                        price is SettingsInput.Result.Error -> error = price.message
                        cap is SettingsInput.Result.Ok && price is SettingsInput.Result.Ok -> onSave(
                            if (type == EnergyType.EV) {
                                settings.copy(energyType = type, evCapacityKWh = cap.value, evPriceWonPerKWh = price.value)
                            } else {
                                settings.copy(energyType = type, fuelCapacityL = cap.value, fuelPriceWonPerL = price.value)
                            },
                        )
                    }
                },
                modifier = Modifier.heightIn(min = CarDimens.touch),
            ) { Text("저장", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = CarDimens.touch)) {
                Text(if (firstRun) "기본값으로 시작" else "취소", style = MaterialTheme.typography.labelLarge)
            }
        }

        // F10 데모 모드: 시연·개발용이라 홈이 아니라 설정 안에 둔다 (UI_GUIDELINES.md 4절: 홈 버튼은 2~3개)
        if (!firstRun && !tripInProgress) {
            HorizontalDivider()
            Text("데모 모드", style = MaterialTheme.typography.headlineMedium)
            Text(
                "실제 차량 값 대신 앱 안의 버튼으로 주행·정차·충전을 흉내 내요. 켜 두는 동안 화면에 \"데모 모드\"가 표시돼요.",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleText,
            )
            SecondaryButton(if (demoMode) "데모 모드 끄기" else "데모 모드 켜기", onToggleDemo)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || (decimal && it == '.') }.take(7)) },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.widthIn(min = 280.dp, max = 420.dp),
    )
}

private fun trimZero(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gachiga.app.domain.ExpenseCategory
import com.gachiga.app.ui.theme.CarDimens

/** F1 인원 선택: 운전자 포함 1~9명, 큰 버튼. */
@Composable
fun HeadcountDialog(onChosen: (Int) -> Unit, onDismiss: () -> Unit) {
    CarDialog(onDismiss) {
        Text("몇 명이 함께 가나요?", style = MaterialTheme.typography.headlineMedium)
        Text("운전자를 포함한 인원이에요.", style = MaterialTheme.typography.bodyMedium)
        for (row in listOf(1..3, 4..6, 7..9)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
                for (n in row) {
                    Button(onClick = { onChosen(n) }, modifier = Modifier.widthIn(min = 120.dp).heightIn(min = CarDimens.touch)) {
                        Text("${n}명", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text("취소") }
    }
}

/**
 * 금액 입력 (F3 주유 금액, F4 기타 비용). 숫자 패드로 받는다.
 * [showCategories]가 true면 통행료·주차비·기타 중 하나를 고른다.
 */
@Composable
fun AmountDialog(
    title: String,
    subtitle: String?,
    showCategories: Boolean,
    dismissLabel: String,
    onConfirm: (ExpenseCategory, Int) -> Unit,
    onDismiss: () -> Unit,
    onInputStarted: () -> Unit = {},
) {
    var digits by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.TOLL) }

    CarDialog(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        if (showCategories) {
            Row(horizontalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
                ExpenseCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label(), style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = CarDimens.touch),
                    )
                }
            }
        }
        Text(
            if (digits.isEmpty()) "0원" else won(digits.toLong()),
            style = MaterialTheme.typography.displayMedium,
        )
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "←"))
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
                for (key in row) {
                    OutlinedButton(
                        onClick = {
                            onInputStarted()
                            digits = when (key) {
                                "←" -> digits.dropLast(1)
                                else -> (digits + key).trimStart('0').take(MAX_DIGITS)
                            }
                        },
                        modifier = Modifier.widthIn(min = 120.dp).heightIn(min = CarDimens.touch),
                    ) { Text(key, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text(dismissLabel) }
            Button(
                onClick = { onConfirm(category, digits.toInt()) },
                enabled = digits.isNotEmpty(),
                modifier = Modifier.heightIn(min = CarDimens.touch),
            ) { Text("확인") }
        }
    }
}

@Composable
fun ConfirmEndDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("여행을 끝낼까요?", style = MaterialTheme.typography.headlineMedium) },
        text = { Text("기록을 마치고 정산 결과를 보여 드려요.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = onConfirm, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text("여행 종료") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text("계속 기록") } },
    )
}

@Composable
fun MessageDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text("확인") } },
    )
}

@Composable
private fun CarDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

private const val MAX_DIGITS = 8 // 9,999만 원까지

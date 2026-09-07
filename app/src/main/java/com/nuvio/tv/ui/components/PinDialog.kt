package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioRadii
import com.nuvio.tv.ui.theme.NuvioTheme

private const val PIN_DIGIT_COUNT = 4

/**
 * D-pad PIN entry: Up/Down cycles 0-9 in the selected cell, Left/Right moves
 * the cell, OK confirms once all four digits are filled, Back dismisses.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PinDialog(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var digits by remember { mutableStateOf(List(PIN_DIGIT_COUNT) { -1 }) }
    var selectedIndex by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val cellShape = RoundedCornerShape(NuvioRadii.tokens.md)

    // Restart entry whenever the failure message changes (e.g. wrong PIN).
    LaunchedEffect(error) {
        if (error != null) {
            digits = List(PIN_DIGIT_COUNT) { -1 }
            selectedIndex = 0
        }
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = error ?: subtitle,
        width = 460.dp,
        suppressFirstKeyUp = false
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Start)
                .selectableGroup()
                .focusable()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            digits = digits.toMutableList().also {
                                it[selectedIndex] = (it[selectedIndex].coerceAtLeast(0) + 9) % 10
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            digits = digits.toMutableList().also {
                                it[selectedIndex] = (it[selectedIndex].coerceAtLeast(0) + 1) % 10
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionRight -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(PIN_DIGIT_COUNT - 1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (digits.all { it in 0..9 }) {
                                onConfirm(digits.joinToString(""))
                            }
                            true
                        }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PIN_DIGIT_COUNT) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isSelected) {
                                NuvioTheme.colors.FocusBackground
                            } else {
                                NuvioTheme.colors.BackgroundCard
                            },
                            shape = cellShape
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = NuvioTheme.spacing.xxs,
                                    color = NuvioTheme.colors.FocusRing,
                                    shape = cellShape
                                )
                            } else {
                                Modifier.border(
                                    width = NuvioTheme.spacing.hairline,
                                    color = NuvioTheme.colors.Border,
                                    shape = cellShape
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val value = digits[index]
                    Text(
                        text = if (value in 0..9) value.toString() else "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (value in 0..9) {
                            NuvioTheme.colors.TextPrimary
                        } else {
                            NuvioTheme.colors.TextTertiary
                        }
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(NuvioTheme.spacing.md)
        )
        Text(
            text = androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.pin_confirm_hint),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

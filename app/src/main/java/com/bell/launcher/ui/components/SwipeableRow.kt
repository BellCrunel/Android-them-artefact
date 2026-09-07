package com.bell.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Рядок, який можна змахнути вбік.
 *
 * Свайп зліва направо — відкрити сповіщення, справа наліво — прибрати його.
 * Якщо [enabled] = false, рядок поводиться як звичайний (щоб не заважати скролу).
 */
@Composable
fun SwipeableRow(
    enabled: Boolean,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    hintOpen: String = "Відкрити",
    hintDismiss: String = "Прибрати",
    hintColor: Color = Color.White,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 92.dp.toPx() }
    val maxDrag = with(LocalDensity.current) { 150.dp.toPx() }

    Box(modifier.fillMaxWidth()) {
        // Підказка під рядком — видно тільки під час свайпу
        if (offsetX.value != 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(hintColor.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (offsetX.value > 0) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    text = if (offsetX.value > 0) hintOpen else hintDismiss,
                    color = hintColor.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(enabled) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offsetX.value
                            scope.launch {
                                when {
                                    value > threshold -> {
                                        offsetX.animateTo(0f, tween(180))
                                        onSwipeRight()
                                    }
                                    value < -threshold -> {
                                        offsetX.animateTo(0f, tween(180))
                                        onSwipeLeft()
                                    }
                                    else -> offsetX.animateTo(0f, tween(180))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + amount).coerceIn(-maxDrag, maxDrag))
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Маленька крапка-лічильник біля назви додатка. */
@Composable
fun NotificationDot(
    count: Int,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = if (count > 1) 6.dp else 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 1) count.toString() else " ",
            color = textColor,
            fontSize = 10.sp,
        )
    }
}

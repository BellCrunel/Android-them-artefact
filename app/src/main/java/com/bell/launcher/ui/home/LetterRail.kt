package com.bell.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Вертикальний алфавітний покажчик уздовж правого краю.
 *
 * Літери біля пальця збільшуються й трохи виїжджають ліворуч — так у Niagara
 * видно, яку саме літеру ти зараз тримаєш, не закриваючи її пальцем.
 */
@Composable
fun LetterRail(
    letters: List<String>,
    active: String?,
    color: Color,
    onActiveChange: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    var trackHeight by remember { mutableFloatStateOf(0f) }
    val activeIndex = letters.indexOf(active)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(34.dp)
            .onGloballyPositioned { trackHeight = it.size.height.toFloat() }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()

                    fun pick(y: Float) {
                        if (trackHeight <= 0f) return
                        val clamped = y.coerceIn(0f, trackHeight)
                        val index = ((clamped / trackHeight) * letters.size)
                            .toInt().coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        if (letter != active) onActiveChange(letter)
                    }

                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    onRelease()
                }
            }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = if (activeIndex < 0) 99 else abs(index - activeIndex)
            val boost = (2 - distance).coerceAtLeast(0)          // 2 біля пальця, далі 0
            val scale by animateFloatAsState(
                targetValue = 1f + boost * 0.30f,
                animationSpec = tween(120),
                label = "letterScale",
            )
            val shift by animateFloatAsState(
                targetValue = -boost * 5f,
                animationSpec = tween(120),
                label = "letterShift",
            )

            Text(
                text = letter,
                color = color.copy(alpha = if (distance == 0) 1f else 0.65f),
                fontSize = (11f * scale).sp,
                fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.offset(x = shift.dp),
            )
        }
    }
}

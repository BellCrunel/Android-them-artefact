package com.bell.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private val ITEM_HEIGHT = 21.dp

/** Ширина зони дотику — під великий палець, а не під олівець. */
val RAIL_WIDTH = 68.dp

private val BUBBLE_SIZE = 46.dp

/**
 * Алфавітний покажчик уздовж правого краю.
 *
 * Під час протягування літери вигинаються дугою ліворуч — амплітуда згасає
 * за гаусом від пальця, тому дуга плавна, без сходинок. Ліворуч від дуги
 * висить бульбашка з поточною літерою, щоб її не закривав палець.
 *
 * Літера змінюється **безперервно** під час руху пальця, а не лише на тап.
 */
@Composable
fun LetterRail(
    letters: List<String>,
    active: String?,
    color: Color,
    bubbleColor: Color,
    bubbleTextColor: Color,
    onActiveChange: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val density = LocalDensity.current
    val itemPx = with(density) { ITEM_HEIGHT.toPx() }
    val bubbleHalfPx = with(density) { (BUBBLE_SIZE / 2).toPx() }
    val bubbleShiftPx = with(density) { 74.dp.toPx() }

    // Наскільки далеко вигинається дуга і як швидко згасає
    val amplitudePx = with(density) { 78.dp.toPx() }
    val spreadPx = with(density) { 96.dp.toPx() }

    var pointerY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val bend by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(160),
        label = "bend",
    )

    Box(modifier) {
        Column(
            modifier = Modifier
                .height(ITEM_HEIGHT * letters.size)
                .width(RAIL_WIDTH)
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true

                        fun pick(y: Float) {
                            pointerY = y.coerceIn(0f, itemPx * letters.size)
                            val index = (pointerY / itemPx).toInt()
                                .coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            if (letter != active) onActiveChange(letter)
                        }

                        pick(down.position.y)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // Споживаємо, щоб список під пальцем не скролився
                            change.consume()
                            pick(change.position.y)
                        }

                        dragging = false
                        onRelease()
                    }
                },
        ) {
            letters.forEachIndexed { index, letter ->
                val center = (index + 0.5f) * itemPx
                val distance = abs(center - pointerY)
                val falloff = exp(-(distance / spreadPx) * (distance / spreadPx))
                val shift = -amplitudePx * falloff * bend
                val grow = 1f + 0.55f * falloff * bend
                val isActive = letter == active && dragging

                Box(
                    Modifier
                        .height(ITEM_HEIGHT)
                        .fillMaxWidth()
                        .padding(end = 12.dp),
                    // Літери притиснуті до правого краю, а зона дотику широка
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    // До дотику алфавіт майже прозорий, під час протягування — проявляється
                    val alpha = if (isActive) {
                        1f
                    } else {
                        (0.26f + 0.48f * bend + 0.26f * falloff * bend).coerceAtMost(0.95f)
                    }

                    Text(
                        text = letter,
                        color = color.copy(alpha = alpha),
                        fontSize = (11f * grow).sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.offset { IntOffset(shift.roundToInt(), 0) },
                    )
                }
            }
        }

        if (dragging && active != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -bubbleShiftPx.roundToInt(),
                            y = (pointerY - bubbleHalfPx).roundToInt(),
                        )
                    }
                    .size(BUBBLE_SIZE)
                    .background(bubbleColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = active,
                    color = bubbleTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

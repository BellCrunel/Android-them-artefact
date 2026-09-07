package com.bell.launcher.ui.home

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private val ITEM_HEIGHT = 21.dp

/** Ширина зони дотику — під великий палець. */
val RAIL_WIDTH = 76.dp

private val BUBBLE_SIZE = 46.dp

/**
 * Алфавітний покажчик уздовж бічного краю (правого або лівого).
 *
 * Продуктивність: деформація літер рахується в [graphicsLayer], а не в тілі
 * composable. Читання позиції пальця всередині graphicsLayer відкладає
 * інвалідацію до фази малювання — Compose не перескладає 26 Text на кожен
 * рух пальця, тому дуга йде рівно, без ривків.
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
    /** Смуга ліворуч — дзеркальне розташування для лівої руки. */
    onLeft: Boolean = false,
    /** Тихий клац і легка вібрація на кожній новій літері. */
    feedback: Boolean = true,
) {
    if (letters.isEmpty()) return

    val density = LocalDensity.current
    val itemPx = with(density) { ITEM_HEIGHT.toPx() }
    val bubbleHalfPx = with(density) { (BUBBLE_SIZE / 2).toPx() }
    val bubbleShiftPx = with(density) { 86.dp.toPx() }
    val amplitudePx = with(density) { 78.dp.toPx() }
    val spreadPx = with(density) { 96.dp.toPx() }

    // Системний View потрібен, щоб клац і вібрація підкорялися налаштуванням
    // телефона: у беззвучному режимі звуку не буде, з вимкненою вібрацією — тряски.
    val view = LocalView.current

    val pointerY = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val bendState = animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(140),
        label = "bend",
    )

    // Ліворуч літери вигинаються вправо, праворуч — вліво.
    val direction = if (onLeft) 1f else -1f
    val origin = if (onLeft) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)

    Box(modifier) {
        Column(
            modifier = Modifier
                .height(ITEM_HEIGHT * letters.size)
                .width(RAIL_WIDTH)
                .pointerInput(letters, feedback) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true

                        // Літера, на якій палець стоїть зараз. Порівнювати з [active]
                        // не можна: воно приходить назад через рекомпозицію із
                        // затримкою, і на швидкому русі клац лунав би двічі.
                        var current: String? = null

                        fun pick(y: Float) {
                            pointerY.floatValue = y.coerceIn(0f, itemPx * letters.size)
                            val index = (pointerY.floatValue / itemPx).toInt()
                                .coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            if (letter == current) return
                            current = letter
                            if (feedback) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            }
                            onActiveChange(letter)
                        }

                        pick(down.position.y)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
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
                val isActive = letter == active

                Box(
                    Modifier
                        .height(ITEM_HEIGHT)
                        .fillMaxWidth()
                        .padding(start = if (onLeft) 12.dp else 0.dp, end = if (onLeft) 0.dp else 12.dp),
                    contentAlignment = if (onLeft) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Text(
                        text = letter,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            val bend = bendState.value
                            val distance = abs(center - pointerY.floatValue)
                            val ratio = distance / spreadPx
                            val falloff = exp(-ratio * ratio)

                            translationX = direction * amplitudePx * falloff * bend
                            val grow = 1f + 0.55f * falloff * bend
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = origin
                            alpha = (0.26f + 0.48f * bend + 0.26f * falloff * bend)
                                .coerceAtMost(1f)
                        },
                    )
                }
            }
        }

        if (dragging && active != null) {
            Box(
                Modifier
                    .align(if (onLeft) Alignment.TopStart else Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            // Бульбашка завжди зсувається всередину екрана.
                            x = (direction * bubbleShiftPx).roundToInt(),
                            y = (pointerY.floatValue - bubbleHalfPx).roundToInt(),
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

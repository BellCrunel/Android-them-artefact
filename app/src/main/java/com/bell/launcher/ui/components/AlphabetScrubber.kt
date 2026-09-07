package com.bell.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Алфавітний скрол справа: колонка літер і кругла бульбашка з поточною літерою —
 * як на другому скріні. Працює і на тап, і на протягування.
 */
@Composable
fun AlphabetScrubber(
    letters: List<String>,
    letterColor: Color,
    bubbleColor: Color,
    bubbleTextColor: Color,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val density = LocalDensity.current
    var trackHeight by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }

    val bubbleSize = 46.dp
    val bubbleHalfPx = with(density) { (bubbleSize / 2).toPx() }
    val bubbleOffsetXPx = with(density) { (-58).dp.roundToPx() }

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .onGloballyPositioned { trackHeight = it.size.height.toFloat() }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        fun pick(y: Float) {
                            if (trackHeight <= 0f) return
                            pointerY = y.coerceIn(0f, trackHeight)
                            val index = ((pointerY / trackHeight) * letters.size)
                                .toInt().coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            if (letter != active) {
                                active = letter
                                onLetter(letter)
                            }
                        }

                        pick(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        active = null
                    }
                }
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    color = letterColor.copy(alpha = if (letter == active) 1f else 0.7f),
                    fontSize = 12.sp,
                    fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }

        val current = active
        if (current != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(bubbleOffsetXPx, (pointerY - bubbleHalfPx).roundToInt()) }
                    .size(bubbleSize)
                    .background(bubbleColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current,
                    color = bubbleTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

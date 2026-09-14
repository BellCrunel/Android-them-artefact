package com.bell.launcher.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Спостерігач жестів, який НЕ споживає події — тому список, іконки та віджети
 * продовжують працювати як завжди.
 *
 * Жест живе не на всій ширині, а в смузі [bandStart]…[bandEnd] (частки ширини
 * екрана). Причин дві: в одному краю живе алфавітний покажчик — без вилучення
 * протягування по літерах угору відкривало б пошук, а вниз шторку сповіщень;
 * а в колонці з рядками додатків палець гортає список, і продовження того
 * самого руху не повинно раптом відкривати пошук.
 */
fun Modifier.launcherGestures(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onTwoFingerSwipeDown: () -> Unit,
    bandStart: Float = 0f,
    bandEnd: Float = 1f,
): Modifier = this.pointerInput(
    onSwipeUp,
    onSwipeDown,
    onTwoFingerSwipeDown,
    bandStart,
    bandEnd,
) {
    val threshold = viewConfiguration.touchSlop * 3.5f

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // Дотик почався поза робочою смугою — цей жест не наш
        val x = down.position.x / size.width.coerceAtLeast(1)
        if (x < bandStart || x > bandEnd) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
            }
            return@awaitEachGesture
        }

        var totalX = 0f
        var totalY = 0f
        var maxPointers = 1
        var fired = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed > maxPointers) maxPointers = pressed

            val change = event.changes.firstOrNull { it.id == down.id }
            if (change != null) {
                totalX += change.positionChange().x
                totalY += change.positionChange().y
            }

            if (!fired && abs(totalY) > threshold && abs(totalY) > abs(totalX) * 1.6f) {
                fired = true
                if (totalY < 0) {
                    onSwipeUp()
                } else {
                    if (maxPointers >= 2) onTwoFingerSwipeDown() else onSwipeDown()
                }
            }

            if (event.changes.none { it.pressed }) break
        }
    }
}

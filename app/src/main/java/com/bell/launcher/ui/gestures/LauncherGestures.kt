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
 * [excludeRightPx] і [excludeLeftPx] — смуги вздовж бічних країв, де жести
 * лаунчера ігноруються. В одній із них живе алфавітний покажчик: без цієї зони
 * протягування по літерах угору відкривало б пошук, а вниз — шторку сповіщень.
 * Друга смуга — дзеркальна зона під палець іншої руки: там список можна
 * спокійно гортати, не боячись зачепити жест.
 */
fun Modifier.launcherGestures(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onTwoFingerSwipeDown: () -> Unit,
    excludeRightPx: Float = 0f,
    excludeLeftPx: Float = 0f,
): Modifier = this.pointerInput(
    onSwipeUp,
    onSwipeDown,
    onTwoFingerSwipeDown,
    excludeRightPx,
    excludeLeftPx,
) {
    val threshold = viewConfiguration.touchSlop * 3.5f

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // Дотик почався в бічній зоні — цей жест не наш
        val inRight = excludeRightPx > 0f && down.position.x > size.width - excludeRightPx
        val inLeft = excludeLeftPx > 0f && down.position.x < excludeLeftPx
        if (inRight || inLeft) {
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

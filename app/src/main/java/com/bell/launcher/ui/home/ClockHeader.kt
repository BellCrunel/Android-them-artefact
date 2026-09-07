package com.bell.launcher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bell.launcher.data.Weather
import com.bell.launcher.theme.LocalClockFont
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.theme.model.AlignMode
import com.bell.launcher.theme.model.HeaderStyle
import com.bell.launcher.theme.parseColor
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Заголовок домашнього екрана: великі цифри часу + рядок «дата · погода».
 * Повністю керується секцією `header` у manifest.json теми.
 */
@Composable
fun ClockHeader(
    weather: Weather?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.header
    if (spec.style == HeaderStyle.NONE) return

    val clockFont = LocalClockFont.current

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(10_000L)
        }
    }

    val clockColor = parseColor(spec.clockColor, parseColor(theme.manifest.colors.homeLabel, Color.White))
    val subColor = parseColor(spec.subColor, clockColor.copy(alpha = 0.85f))
    val shadow = parseColor(theme.manifest.colors.labelShadow, Color.Transparent)

    val timeText = remember(now.hour, now.minute, spec.hour24, spec.clockSeparator) {
        val pattern = if (spec.hour24) "HH" else "hh"
        val hours = now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        val minutes = now.format(DateTimeFormatter.ofPattern("mm", Locale.getDefault()))
        "$hours${spec.clockSeparator}$minutes"
    }

    val dateText = remember(now.dayOfYear, spec.dateFormat) {
        runCatching {
            now.format(DateTimeFormatter.ofPattern(spec.dateFormat, Locale.getDefault()))
        }.getOrDefault(now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())))
    }

    val subLine = buildList {
        if (spec.style == HeaderStyle.COMPACT) add(timeText)
        if (spec.showDate) add(dateText)
        if (spec.showWeather && weather != null) add(weather.shortText)
    }.joinToString(spec.subSeparator.ifEmpty { " " })

    val horizontalAlignment = when (spec.align) {
        AlignMode.START -> Alignment.Start
        AlignMode.CENTER -> Alignment.CenterHorizontally
        AlignMode.END -> Alignment.End
    }
    val textAlign = when (spec.align) {
        AlignMode.START -> TextAlign.Start
        AlignMode.CENTER -> TextAlign.Center
        AlignMode.END -> TextAlign.End
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spec.paddingTopDp.dp, bottom = spec.paddingBottomDp.dp),
        horizontalAlignment = horizontalAlignment,
    ) {
        if (spec.style == HeaderStyle.BIG_DIGITS) {
            Text(
                text = timeText,
                style = TextStyle(
                    color = clockColor,
                    fontSize = spec.clockSizeSp.sp,
                    fontWeight = FontWeight(spec.clockWeight.coerceIn(100, 900)),
                    fontFamily = clockFont,
                    letterSpacing = spec.clockLetterSpacingEm.em,
                    textAlign = textAlign,
                    shadow = Shadow(color = shadow, blurRadius = 8f),
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
        }

        if (subLine.isNotBlank()) {
            Text(
                text = subLine,
                style = TextStyle(
                    color = subColor,
                    fontSize = spec.subSizeSp.sp,
                    textAlign = textAlign,
                    shadow = Shadow(color = shadow, blurRadius = 6f),
                ),
                maxLines = 1,
            )
        }
    }
}

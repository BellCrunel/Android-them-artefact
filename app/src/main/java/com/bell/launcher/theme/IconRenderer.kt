package com.bell.launcher.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.bell.launcher.theme.model.IconShape
import com.bell.launcher.theme.model.IconSpec
import kotlin.math.min

/**
 * Приводить будь-яку іконку до вигляду, заданого темою:
 * форма, підкладка, масштаб, монохромний відтінок.
 */
object IconRenderer {

    fun render(drawable: Drawable, spec: IconSpec, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (spec.shape == IconShape.ORIGINAL) {
            drawScaled(canvas, drawable, size, 1f)
            return applyTint(out, spec)
        }

        val path = shapePath(spec, size.toFloat())

        // Підкладка
        val bg = parseColorInt(spec.background, 0)
        if (bg != 0) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
            canvas.drawPath(path, paint)
        }

        // Малюємо саму іконку в окремий шар і обрізаємо по формі
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layer)
        val content = unwrap(drawable)
        val scale = if (isAdaptive(drawable)) 1f else spec.scale.coerceIn(0.4f, 1f)
        drawScaled(layerCanvas, content, size, scale)

        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        layerCanvas.drawBitmap(mask, 0f, 0f, maskPaint)
        canvas.drawBitmap(layer, 0f, 0f, null)

        layer.recycle()
        mask.recycle()
        return applyTint(out, spec)
    }

    private fun applyTint(bitmap: Bitmap, spec: IconSpec): Bitmap {
        val tint = parseColorInt(spec.tint, 0)
        if (tint == 0) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        }
        Canvas(out).drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return out
    }

    private fun isAdaptive(drawable: Drawable): Boolean = drawable is AdaptiveIconDrawable

    /** Для адаптивних іконок беремо фон+передній план цілком (вони вже розраховані під маску). */
    private fun unwrap(drawable: Drawable): Drawable = drawable

    private fun drawScaled(canvas: Canvas, drawable: Drawable, size: Int, scale: Float) {
        val inset = ((1f - scale) * size / 2f).toInt()
        val bounds = Rect(inset, inset, size - inset, size - inset)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            canvas.drawBitmap(drawable.bitmap, null, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val old = Rect(drawable.bounds)
            drawable.bounds = bounds
            drawable.draw(canvas)
            drawable.bounds = old
        }
    }

    fun shapePath(spec: IconSpec, size: Float): Path {
        val path = Path()
        val rect = RectF(0f, 0f, size, size)
        when (spec.shape) {
            IconShape.CIRCLE -> path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            IconShape.SQUARE -> path.addRect(rect, Path.Direction.CW)
            IconShape.ROUNDED -> {
                val r = size * spec.cornerRadiusPercent.coerceIn(0, 50) / 100f
                path.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            IconShape.TEARDROP -> {
                val r = size / 2f
                path.addRoundRect(rect, floatArrayOf(r, r, r, r, r, r, size * 0.12f, size * 0.12f), Path.Direction.CW)
            }
            IconShape.HEXAGON -> {
                val cx = size / 2f
                val cy = size / 2f
                val rr = size / 2f
                for (i in 0 until 6) {
                    val a = Math.toRadians((60.0 * i) - 30.0)
                    val x = cx + rr * kotlin.math.cos(a).toFloat()
                    val y = cy + rr * kotlin.math.sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            IconShape.SQUIRCLE -> squircle(path, size)
            IconShape.ORIGINAL -> path.addRect(rect, Path.Direction.CW)
        }
        return path
    }

    /** Superellipse-подібна форма (як у Pixel/One UI). */
    private fun squircle(path: Path, size: Float) {
        val s = size
        val c = s * 0.5f
        val k = s * 0.28f // «сила» заокруглення
        path.moveTo(c, 0f)
        path.cubicTo(c + k, 0f, s, c - k, s, c)
        path.cubicTo(s, c + k, c + k, s, c, s)
        path.cubicTo(c - k, s, 0f, c + k, 0f, c)
        path.cubicTo(0f, c - k, c - k, 0f, c, 0f)
        path.close()
    }

    fun sizeOf(drawable: Drawable, fallback: Int): Int =
        min(fallback, maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).takeIf { it > 0 } ?: fallback)
}

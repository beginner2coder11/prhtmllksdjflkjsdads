package com.web2app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Custom branding loaders whose moving progress is painted with a gradient of the theme
 * color. Both self-animate while attached to a window (no external start/stop needed).
 */
object GradientLoaders {

    /** A lighter shade of [color] for the gradient's leading edge. */
    private fun light(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt()
    )

    /** A circular ring whose gradient (light → full color) rotates continuously. */
    class Ring(
        context: Context,
        private val sizePx: Int,
        private val thicknessPx: Float,
        private val color: Int,
        private val trackColor: Int
    ) : View(context) {

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = thicknessPx
            strokeCap = Paint.Cap.ROUND
            this.color = trackColor
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = thicknessPx
            strokeCap = Paint.Cap.ROUND
        }
        private var angle = 0f
        private var animator: ValueAnimator? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val cx = w / 2f
            val cy = h / 2f
            // Sweep gradient that fades from a light shade up to the full color; rotating
            // the canvas spins the gradient seam, so the "progress" reads as a moving gradient.
            arcPaint.shader = SweepGradient(
                cx, cy,
                intArrayOf(light(color, 0.75f), color, light(color, 0.75f)),
                floatArrayOf(0f, 0.7f, 1f)
            )
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = (Math.min(width, height) - thicknessPx) / 2f
            canvas.drawCircle(cx, cy, r, trackPaint)
            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawCircle(cx, cy, r, arcPaint)
            canvas.restore()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1100
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { angle = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            animator = null
            super.onDetachedFromWindow()
        }
    }

    /** A rounded track with a gradient segment that slides across it continuously. */
    class Bar(
        context: Context,
        private val widthPx: Int,
        private val thicknessPx: Float,
        private val color: Int,
        private val trackColor: Int
    ) : View(context) {

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = trackColor }
        private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clip = Path()
        private val rect = RectF()
        private var progress = 0f
        private var animator: ValueAnimator? = null

        private val segWidth get() = widthPx * 0.45f

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(widthPx, thicknessPx.toInt())
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            clip.reset()
            rect.set(0f, 0f, w.toFloat(), h.toFloat())
            clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            // Track.
            canvas.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2f, h / 2f, trackPaint)
            // Slide the gradient segment from off-screen left to off-screen right.
            val travel = width + segWidth
            val x = -segWidth + progress * travel
            canvas.save()
            canvas.clipPath(clip)
            segPaint.shader = LinearGradient(
                x, 0f, x + segWidth, 0f,
                intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(x, 0f, x + segWidth, h, h / 2f, h / 2f, segPaint)
            canvas.restore()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            animator = null
            super.onDetachedFromWindow()
        }
    }
}

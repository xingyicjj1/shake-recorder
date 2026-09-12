package com.example.shakerecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var amplitudes: FloatArray = floatArrayOf()
    private var progress = 0f

    private val played = -0x1 // white
    private val unplayed = 0x66FFFFFF.toInt()

    fun setAmplitudes(amps: FloatArray) {
        amplitudes = amps
        invalidate()
    }

    fun setProgress(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f
        val barW = w / amplitudes.size
        val gap = barW * 0.35f
        for (i in amplitudes.indices) {
            val frac = if (amplitudes.size > 1) i.toFloat() / (amplitudes.size - 1) else 0f
            val amp = amplitudes[i].coerceIn(0f, 1f)
            val barH = (amp * h * 0.92f).coerceAtLeast(3f)
            val x = i * barW + gap / 2f
            paint.color = if (frac <= progress) played else unplayed
            canvas.drawRoundRect(x, mid - barH / 2f, x + (barW - gap), mid + barH / 2f, 3f, 3f, paint)
        }
    }
}

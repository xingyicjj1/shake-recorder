package com.example.shakerecorder

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs

/**
 * 检测「左右交替摇晃两次」：
 * 在 WINDOW_MS 时间窗口内，出现 4 次单向摆动且方向严格交替
 * （+1,-1,+1,-1 或反向），即判定为有效触发。
 */
class ShakeDetector(private val onTrigger: () -> Unit) : SensorEventListener {

    companion object {
        private const val THRESHOLD = 9.0f     // 加速度阈值 m/s^2（可调）
        private const val WINDOW_MS = 2000L    // 有效时间窗口
        private const val REQUIRED = 4         // 需要的单向摆动次数（左右各两次）
    }

    private val swings = ArrayDeque<Pair<Long, Int>>() // (时间戳, 方向) +1=右 -1=左
    private var lastDirection = 0
    private var lastTrigger = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val now = System.currentTimeMillis()
        if (abs(x) < THRESHOLD) return

        val dir = if (x > 0) 1 else -1
        if (dir == lastDirection) return // 去抖：同方向连续事件忽略
        lastDirection = dir
        swings.addLast(now to dir)

        // 修剪窗口外事件
        while (swings.isNotEmpty() && now - swings.first().first > WINDOW_MS) {
            swings.removeFirst()
        }

        if (swings.size >= REQUIRED) {
            val seq = swings.takeLast(REQUIRED)
            val alternating = seq.zipWithNext().all { (a, b) -> a.second != b.second }
            val span = seq.last().first - seq.first().first
            if (alternating && span <= WINDOW_MS && now - lastTrigger > 1500L) {
                lastTrigger = now
                swings.clear()
                lastDirection = 0
                onTrigger()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        swings.clear()
        lastDirection = 0
    }
}

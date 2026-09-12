package com.example.shakerecorder

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs

/**
 * 检测「左右交替摇晃两次」：
 * 在 WINDOW_MS 时间窗口内，出现 4 次单向摆动且方向严格交替
 * （+1,-1,+1,-1 或反向），即判定为有效触发。
 *
 * 灵敏度调节（2026-09-12 调优：降低门槛 + 放宽窗口 + 加摆动间隔去抖）：
 *  - THRESHOLD 越小越灵敏（普通手摇约 4~6 m/s² 即可触发）
 *  - WINDOW_MS 越大越容易在慢速摇晃下完成
 *  - MIN_GAP_MS 防止单次摆动被重复计数
 */
class ShakeDetector(private val onTrigger: () -> Unit) : SensorEventListener {

    companion object {
        private const val THRESHOLD = 4.5f     // 加速度阈值 m/s^2（调低：更易触发）
        private const val WINDOW_MS = 3000L    // 有效时间窗口（放宽）
        private const val REQUIRED = 4         // 需要的单向摆动次数（左右各两次）
        private const val MIN_GAP_MS = 100L    // 相邻两次摆动最小间隔，去抖
    }

    private val swings = ArrayDeque<Pair<Long, Int>>() // (时间戳, 方向) +1=右 -1=左
    private var lastDirection = 0
    private var lastEvent = 0L
    private var lastSwingTime = 0L
    private var lastTrigger = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        lastEvent = android.os.SystemClock.uptimeMillis()
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val now = System.currentTimeMillis()
        if (abs(x) < THRESHOLD) return

        val dir = if (x > 0) 1 else -1
        if (dir == lastDirection) return           // 同方向连续事件忽略
        if (now - lastSwingTime < MIN_GAP_MS) return // 间隔过短去抖
        lastDirection = dir
        lastSwingTime = now
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
        lastSwingTime = 0L
    }

    fun lastEventAt(): Long = lastEvent
}

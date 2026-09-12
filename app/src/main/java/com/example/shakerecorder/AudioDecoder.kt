package com.example.shakerecorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

object AudioDecoder {
    /**
     * 解码音频文件，按时间轴归一化到 buckets 个包络点（取值 [0,1]）。
     * 失败/全静音返回 null，调用方回退占位波形。
     */
    fun decodeAmplitudes(path: String, buckets: Int = 96): FloatArray? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { trackIndex = i; format = f; break }
            }
            if (trackIndex < 0 || format == null) { extractor.release(); return null }

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            if (durationUs <= 0) { extractor.release(); return null }

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            extractor.selectTrack(trackIndex)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val peaks = FloatArray(buckets)
            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf: ByteBuffer? = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf: ByteBuffer? = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            val n = info.size / 2 // 16bit PCM
                            val sb = outBuf.asShortBuffer()
                            val cnt = minOf(n, sb.remaining())
                            var localMax = 0f
                            for (i in 0 until cnt) {
                                val amp = Math.abs(sb.get(i).toInt()) / 32768f
                                if (amp > localMax) localMax = amp
                            }
                            // 按该输出块的时间位置映射到 bucket
                            val posUs = info.presentationTimeUs
                            val b = (posUs * buckets / durationUs).toInt().coerceIn(0, buckets - 1)
                            if (localMax > peaks[b]) peaks[b] = localMax
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                }
            }

            codec.stop(); codec.release(); extractor.release()

            if (peaks.all { it <= 0f }) null else {
                // 归一化到 0..1，并做一点点平滑填充空洞
                val max = peaks.maxOrNull() ?: 1f
                val out = FloatArray(buckets)
                for (i in 0 until buckets) {
                    val v = peaks[i] / (if (max <= 0f) 1f else max)
                    out[i] = if (v <= 0f && i > 0) out[i - 1] * 0.6f else v.coerceIn(0f, 1f)
                }
                out
            }
        } catch (e: Exception) {
            null
        }
    }
}

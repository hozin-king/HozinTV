package com.hozinking.hozintv.ui

import android.content.Context
import android.media.AudioManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

/**
 * Gesture swipe vertikal di mini player (ringan, tanpa library):
 * - sisi kanan layar: volume naik/turun
 * - sisi kiri layar: kecerahan naik/turun
 *
 * Hanya mengonsumsi event yang jelas-jelas swipe vertikal; selebihnya
 * dikembalikan (return false) agar controller PlayerView tetap berfungsi.
 */
class GestureHelper(
    private val activity: AppCompatActivity,
    private val enabled: () -> Boolean,
    private val onHint: (String) -> Unit
) : View.OnTouchListener {

    private val audio: AudioManager by lazy {
        activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val maxVolume: Int by lazy {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    private var startX = 0f
    private var startY = 0f
    private var swiping = false
    private var baseVolume = 0
    private var baseBrightness = 0.5f

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        if (!enabled()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                swiping = false
                baseVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                baseBrightness = currentBrightness()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - startX
                val dy = e.y - startY
                if (!swiping && abs(dy) > 32f && abs(dy) > abs(dx) * 1.5f) {
                    swiping = true
                }
                if (swiping) {
                    val frac = (startY - e.y) / v.height.coerceAtLeast(1)
                    if (startX > v.width / 2f) {
                        // kanan: volume
                        val vol = (baseVolume + frac * maxVolume).toInt().coerceIn(0, maxVolume)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                        onHint("Volume ${(vol * 100 / maxVolume.coerceAtLeast(1))}%")
                    } else {
                        // kiri: kecerahan
                        val b = (baseBrightness + frac).coerceIn(0.05f, 1f)
                        setBrightness(b)
                        onHint("Kecerahan ${(b * 100).toInt()}%")
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swiping) {
                    swiping = false
                    onHint("")
                    return true
                }
            }
        }
        return false
    }

    private fun currentBrightness(): Float {
        val b = activity.window.attributes.screenBrightness
        return if (b < 0) 0.5f else b
    }

    private fun setBrightness(value: Float) {
        val lp: WindowManager.LayoutParams = activity.window.attributes
        lp.screenBrightness = value
        activity.window.attributes = lp
    }
}

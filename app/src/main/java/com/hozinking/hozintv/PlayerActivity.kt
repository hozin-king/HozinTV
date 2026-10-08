package com.hozinking.hozintv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hozinking.hozintv.databinding.ActivityPlayerBinding
import com.hozinking.hozintv.model.Channel

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var hideNameRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        channels = ChannelHolder.channels
        if (channels.isEmpty()) {
            Toast.makeText(this, "Daftar channel kosong", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(channels.indices)

        binding.btnPrev.setOnClickListener { playChannel(index - 1) }
        binding.btnNext.setOnClickListener { playChannel(index + 1) }
        binding.playerView.setOnClickListener { /* controller bawaan yang handle */ }
        // Live: controller tidak auto-muncul (hanya play/pause manual)
        binding.playerView.controllerAutoShow = false

        playChannel(index)
    }

    private fun playChannel(i: Int) {
        index = ((i % channels.size) + channels.size) % channels.size
        val ch = channels[index]
        releasePlayer()

        val dsFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(ch.userAgent ?: "Mozilla/5.0 (Linux; Android 10) HozinTV/1.0")
            ch.referer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) }
            setConnectTimeoutMs(15000)
            setReadTimeoutMs(15000)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dsFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exo ->
                binding.playerView.player = exo
                exo.setMediaItem(buildMediaItem(ch))
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Gagal memutar (${error.errorCodeName})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        binding.liveBadge.visibility = if (isPlaying) View.VISIBLE else View.GONE
                    }
                })
                exo.prepare()
                exo.play()
            }

        showChannelName(ch.name)
    }

    /** Rakit MediaItem: deteksi HLS/DASH + pasang DRM Widevine bila ada di M3U. */
    private fun buildMediaItem(ch: Channel): MediaItem {
        val mime = when (ch.streamType) {
            "hls" -> MimeTypes.APPLICATION_M3U8
            "dash" -> MimeTypes.APPLICATION_MPD
            else -> when {
                ch.url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                ch.url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }
        val builder = MediaItem.Builder().setUri(ch.url)
        mime?.let { builder.setMimeType(it) }
        if (ch.drmType == "widevine" && !ch.drmLicenseUrl.isNullOrBlank()) {
            builder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(ch.drmLicenseUrl)
                    .build()
            )
        }
        return builder.build()
    }

    private fun showChannelName(name: String) {
        binding.channelNameOverlay.text = name
        binding.channelNameOverlay.visibility = View.VISIBLE
        hideNameRunnable?.let { binding.root.removeCallbacks(it) }
        hideNameRunnable = Runnable { binding.channelNameOverlay.visibility = View.GONE }
        binding.root.postDelayed(hideNameRunnable!!, 3000)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    playChannel(index + 1); return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    playChannel(index - 1); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        binding.playerView.player = null
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}

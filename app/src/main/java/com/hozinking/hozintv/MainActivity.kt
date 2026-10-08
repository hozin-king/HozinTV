package com.hozinking.hozintv

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.recyclerview.widget.LinearLayoutManager
import com.hozinking.hozintv.data.PlaylistRepository
import com.hozinking.hozintv.data.PlaylistSource
import com.hozinking.hozintv.databinding.ActivityMainBinding
import com.hozinking.hozintv.model.Channel
import com.hozinking.hozintv.ui.ChannelAdapter
import com.hozinking.hozintv.ui.FeatureSettingsDialog
import com.hozinking.hozintv.ui.FilterDialog
import com.hozinking.hozintv.ui.GestureHelper
import com.hozinking.hozintv.ui.QualityDialog
import com.hozinking.hozintv.ui.SettingsStore
import kotlinx.coroutines.launch

/**
 * Layar utama ala BitTV:
 * - mini PlayerView 16:9 di atas (judul kiri-atas, badge LIVE kiri-bawah,
 *   tombol kualitas/volume/fullscreen kanan-bawah)
 * - toolbar: logo HozinTV | pill filter region | gear | X
 * - daftar channel vertikal (logo rounded, nama, kategori, highlight pilihan)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: PlaylistRepository
    private lateinit var settings: SettingsStore
    private lateinit var adapter: ChannelAdapter
    private lateinit var gestureHelper: GestureHelper

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    private var allChannels: List<Channel> = emptyList()
    private var sources: List<PlaylistSource> = emptyList()
    private var currentSource: PlaylistSource? = null
    private var activeCategory: String? = null
    private var currentChannel: Channel? = null

    private val hintHandler = Handler(Looper.getMainLooper())
    private var hintRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsStore(this)
        applySecureFlag()

        repo = PlaylistRepository(this)
        sources = repo.getSources()

        adapter = ChannelAdapter { playInMiniPlayer(it) }
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter

        gestureHelper = GestureHelper(
            activity = this,
            enabled = { settings.gesturesEnabled },
            onHint = { showGestureHint(it) }
        )
        binding.miniPlayerView.setOnTouchListener(gestureHelper)

        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnSettings.setOnClickListener { showFeatureSettings() }
        binding.btnQualityMini.setOnClickListener { showQualityDialog() }
        binding.btnVolume.setOnClickListener { toggleMute() }
        binding.btnFullscreen.setOnClickListener { openFullscreen() }
        binding.btnStop.setOnClickListener { finishAffinity() }
        binding.emptyView.setOnClickListener { loadChannels() }

        val lastId = repo.getLastSourceId()
        currentSource = sources.find { it.id == lastId } ?: sources.first()
        updateFilterButton()

        loadChannels()
    }

    // ---------------- mini player ----------------

    private fun playInMiniPlayer(ch: Channel) {
        currentChannel = ch
        releasePlayer()

        val dsFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(ch.userAgent ?: "Mozilla/5.0 (Linux; Android 10) HozinTV/1.0")
            ch.referer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) }
            setConnectTimeoutMs(15000)
            setReadTimeoutMs(15000)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dsFactory)
        if (trackSelector == null) trackSelector = DefaultTrackSelector(this)

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector!!)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exo ->
                binding.miniPlayerView.player = exo
                exo.setMediaItem(buildMediaItem(ch))
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.play_error, error.errorCodeName),
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

        binding.miniPlaceholder.visibility = View.GONE
        binding.miniTitle.text = ch.name
        binding.miniTitle.visibility = View.VISIBLE
        binding.btnVolume.setImageResource(R.drawable.ic_volume)
        adapter.setSelected(ch.url)
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

    private fun toggleMute() {
        val p = player ?: return
        if (p.volume > 0f) {
            p.volume = 0f
            binding.btnVolume.setImageResource(R.drawable.ic_volume_off)
            binding.btnVolume.contentDescription = getString(R.string.unmute)
        } else {
            p.volume = 1f
            binding.btnVolume.setImageResource(R.drawable.ic_volume)
            binding.btnVolume.contentDescription = getString(R.string.mute)
        }
    }

    private fun resetPlayback() {
        currentChannel = null
        releasePlayer()
        adapter.setSelected(null)
        binding.miniPlaceholder.visibility = View.VISIBLE
        binding.miniTitle.visibility = View.GONE
        binding.liveBadge.visibility = View.GONE
        binding.btnVolume.setImageResource(R.drawable.ic_volume)
        binding.btnVolume.contentDescription = getString(R.string.mute)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        binding.miniPlayerView.player = null
    }

    private fun openFullscreen() {
        val ch = currentChannel ?: return
        val list = adapter.currentList
        ChannelHolder.channels = list
        player?.pause()
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_INDEX, list.indexOf(ch).coerceAtLeast(0))
        startActivity(intent)
    }

    // ---------------- daftar + filter ----------------

    private fun loadChannels() {
        val src = currentSource ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        lifecycleScope.launch {
            try {
                allChannels = repo.loadChannels(src)
                ChannelHolder.channels = allChannels
                applyFilters()
                // autoplay channel pertama ala BitTV
                if (currentChannel == null && allChannels.isNotEmpty()) {
                    playInMiniPlayer(allChannels[0])
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal load playlist: ${e.message}", Toast.LENGTH_LONG).show()
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = getString(R.string.load_failed)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun applyFilters() {
        var list = allChannels
        activeCategory?.let { cat -> list = list.filter { it.group == cat } }
        adapter.submitList(list)
        binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isNotEmpty()) binding.emptyView.text = getString(R.string.empty_channels)
    }

    private fun updateFilterButton() {
        val label = currentSource?.label ?: getString(R.string.all_categories)
        binding.btnFilter.text = "$label ▾"
    }

    private fun switchToSource(source: PlaylistSource) {
        currentSource = source
        repo.setLastSourceId(source.id)
        activeCategory = null
        updateFilterButton()
        resetPlayback()
        loadChannels()
    }

    // ---------------- dialog ----------------

    private fun showFilterDialog() {
        val categories = allChannels.mapNotNull { it.group }
            .distinct()
            .sorted()
            .map { g -> g to allChannels.count { it.group == g } }
        FilterDialog(
            activity = this,
            sources = sources,
            currentSourceId = currentSource?.id ?: "",
            categories = categories,
            currentCategory = activeCategory,
            listener = object : FilterDialog.Listener {
                override fun onRegionSelected(source: PlaylistSource) = switchToSource(source)

                override fun onCategorySelected(category: String?) {
                    activeCategory = category
                    updateFilterButton()
                    applyFilters()
                }
            }
        ).show()
    }

    private fun showFeatureSettings() {
        FeatureSettingsDialog(
            activity = this,
            store = settings,
            repo = repo,
            currentSourceId = currentSource?.id ?: "",
            onApplied = {
                applySecureFlag()
                Toast.makeText(this, R.string.apply, Toast.LENGTH_SHORT).show()
            },
            onPlaylistsChanged = {
                sources = repo.getSources()
                val lastId = repo.getLastSourceId()
                val src = sources.find { it.id == lastId } ?: sources.first()
                switchToSource(src)
            }
        ).show()
    }

    private fun showQualityDialog() {
        val p = player
        val ts = trackSelector
        if (p == null || ts == null) {
            Toast.makeText(this, R.string.no_tracks, Toast.LENGTH_LONG).show()
            return
        }
        QualityDialog(this, p, ts).show()
    }

    // ---------------- sistem ----------------

    private fun applySecureFlag() {
        if (settings.secureFlag) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun showGestureHint(text: String) {
        hintRunnable?.let { hintHandler.removeCallbacks(it) }
        if (text.isEmpty()) {
            binding.gestureHint.visibility = View.GONE
            return
        }
        binding.gestureHint.text = text
        binding.gestureHint.visibility = View.VISIBLE
        hintRunnable = Runnable { binding.gestureHint.visibility = View.GONE }
        hintHandler.postDelayed(hintRunnable!!, 900)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = player
        if (settings.pipEnabled && p != null && p.isPlaying &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPause() {
        super.onPause()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        if (!inPip) player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        hintRunnable?.let { hintHandler.removeCallbacks(it) }
        releasePlayer()
    }
}

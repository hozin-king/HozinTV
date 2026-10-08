package com.hozinking.hozintv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.hozinking.hozintv.R
import com.hozinking.hozintv.databinding.DialogQualityBinding

/**
 * Dialog kualitas ala BitTV: tab "Video" | "Audio", opsi radio
 * ("Otomatis" + daftar track aktual dari ExoPlayer), tombol Apply.
 * Apply memakai selection override pada DefaultTrackSelector;
 * "Otomatis" menghapus override untuk renderer tab aktif.
 */
class QualityDialog(
    private val activity: AppCompatActivity,
    private val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector
) {

    private data class TrackOpt(
        val rendererIndex: Int,
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String
    )

    private var activeTab = 0 // 0 = video, 1 = audio
    private var videoOpts: List<TrackOpt> = emptyList()
    private var audioOpts: List<TrackOpt> = emptyList()

    fun show() {
        videoOpts = collectTracks(C.TRACK_TYPE_VIDEO)
        audioOpts = collectTracks(C.TRACK_TYPE_AUDIO)
        if (videoOpts.isEmpty() && audioOpts.isEmpty()) {
            Toast.makeText(activity, R.string.no_tracks, Toast.LENGTH_LONG).show()
            return
        }

        val binding = DialogQualityBinding.inflate(LayoutInflater.from(activity))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()

        val adapter = QualityAdapter()
        binding.qualityList.layoutManager = LinearLayoutManager(activity)
        binding.qualityList.adapter = adapter

        fun refresh() {
            val opts = if (activeTab == 0) videoOpts else audioOpts
            adapter.submitList(opts, currentSelectionIndex(opts))
        }

        binding.qualityTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activeTab = tab.position
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.btnQualityApply.setOnClickListener {
            applySelection(if (activeTab == 0) videoOpts else audioOpts, adapter.checked)
            dialog.dismiss()
        }

        refresh()
        dialog.show()
    }

    private fun applySelection(opts: List<TrackOpt>, checked: Int) {
        if (opts.isEmpty()) return
        val r = opts.first().rendererIndex
        if (checked <= 0) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters().clearSelectionOverrides(r)
            )
        } else {
            val opt = opts[checked - 1]
            val groups = trackSelector.currentMappedTrackInfo!!.getTrackGroups(opt.rendererIndex)
            val override = DefaultTrackSelector.SelectionOverride(opt.groupIndex, opt.trackIndex)
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setSelectionOverride(opt.rendererIndex, groups, override)
            )
        }
    }

    private fun collectTracks(type: Int): List<TrackOpt> {
        val info = trackSelector.currentMappedTrackInfo ?: return emptyList()
        val out = mutableListOf<TrackOpt>()
        for (r in 0 until info.rendererCount) {
            if (info.getRendererType(r) != type) continue
            val groups = info.getTrackGroups(r)
            for (g in 0 until groups.length) {
                val group = groups[g]
                for (t in 0 until group.length) {
                    out.add(TrackOpt(r, g, t, trackLabel(type, group.getFormat(t))))
                }
            }
        }
        return out
    }

    private fun trackLabel(type: Int, f: Format): String {
        return if (type == C.TRACK_TYPE_VIDEO) {
            val res = if (f.width > 0 && f.height > 0) "${f.width} × ${f.height}" else "?"
            val br = if (f.bitrate > 0) ", ${"%.2f".format(f.bitrate / 1_000_000.0)} Mbps" else ""
            "$res$br"
        } else {
            val name = f.language?.takeIf { it.isNotBlank() }
                ?: f.label?.takeIf { it.isNotBlank() }
                ?: "Audio"
            val br = if (f.bitrate > 0) ", ${f.bitrate / 1000} kbps" else ""
            "$name$br"
        }
    }

    /** Index pilihan saat ini (0 = Otomatis bila tidak ada override manual). */
    private fun currentSelectionIndex(opts: List<TrackOpt>): Int {
        if (opts.isEmpty()) return 0
        val info = trackSelector.currentMappedTrackInfo ?: return 0
        val r = opts.first().rendererIndex
        val override = trackSelector.parameters.getSelectionOverride(r, info.getTrackGroups(r))
        if (override == null) return 0
        val idx = opts.indexOfFirst {
            it.groupIndex == override.groupIndex && it.trackIndex == override.tracks[0]
        }
        return if (idx >= 0) idx + 1 else 0
    }

    private class QualityAdapter : RecyclerView.Adapter<QualityAdapter.VH>() {

        private var opts: List<TrackOpt> = emptyList()
        var checked = 0

        fun submitList(list: List<TrackOpt>, selected: Int) {
            opts = list
            checked = selected
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.qualityName)
            val radio: RadioButton = v.findViewById(R.id.qualityRadio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_quality, parent, false)
            return VH(v)
        }

        override fun getItemCount() = opts.size + 1 // +1 untuk "Otomatis"

        override fun onBindViewHolder(h: VH, position: Int) {
            val ctx = h.itemView.context
            h.name.text = if (position == 0) ctx.getString(R.string.quality_auto)
                else opts[position - 1].label
            h.radio.isChecked = position == checked
            h.itemView.setOnClickListener {
                checked = position
                notifyDataSetChanged()
            }
        }
    }
}

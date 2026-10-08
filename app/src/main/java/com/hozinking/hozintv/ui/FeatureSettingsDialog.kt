package com.hozinking.hozintv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hozinking.hozintv.R
import com.hozinking.hozintv.data.PlaylistRepository
import com.hozinking.hozintv.data.PlaylistSource
import com.hozinking.hozintv.databinding.DialogFeatureSettingsBinding

/**
 * Dialog "Pengaturan Fitur" ala BitTV:
 * - toggle PiP, Gesture Swipe, dan Blokir screenshot/rekaman (FLAG_SECURE).
 *   Batal = buang, Apply = simpan + terapkan langsung.
 * - section "Playlist M3U": daftar URL playlist tersimpan, tambah URL baru,
 *   hapus per item (custom saja), ketuk untuk ganti playlist aktif.
 */
class FeatureSettingsDialog(
    private val activity: AppCompatActivity,
    private val store: SettingsStore,
    private val repo: PlaylistRepository,
    private var currentSourceId: String,
    private val onApplied: () -> Unit,
    private val onPlaylistsChanged: () -> Unit
) {
    fun show() {
        val binding = DialogFeatureSettingsBinding.inflate(LayoutInflater.from(activity))
        binding.switchPip.isChecked = store.pipEnabled
        binding.switchGesture.isChecked = store.gesturesEnabled
        binding.switchSecure.isChecked = store.secureFlag

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()

        val adapter = PlaylistAdapter(
            onSelect = { src ->
                currentSourceId = src.id
                repo.setLastSourceId(src.id)
                adapter.refresh(repo.getSources(), currentSourceId)
                onPlaylistsChanged()
            },
            onDelete = { src -> confirmDelete(binding, adapter, src) }
        )
        binding.playlistList.layoutManager = LinearLayoutManager(activity)
        binding.playlistList.adapter = adapter
        adapter.refresh(repo.getSources(), currentSourceId)

        binding.btnAddPlaylist.setOnClickListener { showAddDialog(binding, adapter) }
        binding.btnFeatCancel.setOnClickListener { dialog.dismiss() }
        binding.btnFeatApply.setOnClickListener {
            store.pipEnabled = binding.switchPip.isChecked
            store.gesturesEnabled = binding.switchGesture.isChecked
            store.secureFlag = binding.switchSecure.isChecked
            dialog.dismiss()
            onApplied()
        }
        dialog.show()
    }

    private fun showAddDialog(
        binding: DialogFeatureSettingsBinding,
        adapter: PlaylistAdapter
    ) {
        val ctx = activity
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val inputName = TextInputEditText(ctx).apply { hint = ctx.getString(R.string.add_source_name_hint) }
        val inputUrl = TextInputEditText(ctx).apply { hint = ctx.getString(R.string.add_source_url_hint) }
        layout.addView(inputName)
        layout.addView(inputUrl)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.add_source_title)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val label = inputName.text.toString().trim().ifEmpty { "Playlist Custom" }
                val url = inputUrl.text.toString().trim()
                if (url.isEmpty() || !url.startsWith("http")) {
                    Toast.makeText(ctx, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repo.addCustomSource(label, url)
                adapter.refresh(repo.getSources(), currentSourceId)
                Toast.makeText(ctx, R.string.source_added, Toast.LENGTH_SHORT).show()
                onPlaylistsChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(
        binding: DialogFeatureSettingsBinding,
        adapter: PlaylistAdapter,
        src: PlaylistSource
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_playlist_title)
            .setMessage(activity.getString(R.string.delete_playlist_msg, src.label))
            .setPositiveButton(R.string.delete) { _, _ ->
                val wasCurrent = src.id == currentSourceId
                repo.removeCustomSource(src)
                val remaining = repo.getSources()
                if (wasCurrent) {
                    currentSourceId = remaining.first().id
                    repo.setLastSourceId(currentSourceId)
                }
                adapter.refresh(remaining, currentSourceId)
                Toast.makeText(activity, R.string.source_deleted, Toast.LENGTH_SHORT).show()
                onPlaylistsChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class PlaylistAdapter(
        private val onSelect: (PlaylistSource) -> Unit,
        private val onDelete: (PlaylistSource) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

        private var items: List<PlaylistSource> = emptyList()
        private var currentId: String = ""

        fun refresh(list: List<PlaylistSource>, current: String) {
            items = list
            currentId = current
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.playlistName)
            val check: ImageView = v.findViewById(R.id.playlistCheck)
            val delete: ImageButton = v.findViewById(R.id.btnDeletePlaylist)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val src = items[position]
            h.name.text = src.label
            h.check.visibility = if (src.id == currentId) View.VISIBLE else View.GONE
            h.delete.visibility = if (src.custom) View.VISIBLE else View.GONE
            h.itemView.setOnClickListener { onSelect(src) }
            h.delete.setOnClickListener { onDelete(src) }
        }
    }
}

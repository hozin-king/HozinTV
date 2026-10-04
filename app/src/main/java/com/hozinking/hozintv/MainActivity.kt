package com.hozinking.hozintv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hozinking.hozintv.data.PlaylistRepository
import com.hozinking.hozintv.data.PlaylistSource
import com.hozinking.hozintv.databinding.ActivityMainBinding
import com.hozinking.hozintv.model.Channel
import com.hozinking.hozintv.ui.ChannelAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: PlaylistRepository
    private lateinit var adapter: ChannelAdapter
    private var allChannels: List<Channel> = emptyList()
    private var sources: List<PlaylistSource> = emptyList()
    private var currentSource: PlaylistSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = PlaylistRepository(this)
        sources = repo.getSources()

        val span = (resources.displayMetrics.widthPixels / resources.displayMetrics.density / 190).toInt().coerceAtLeast(2)
        binding.channelList.layoutManager = GridLayoutManager(this, span)
        adapter = ChannelAdapter { openPlayer(it) }
        binding.channelList.adapter = adapter

        setupSourceDropdown()
        setupSearch()

        binding.btnAddSource.setOnClickListener { showAddSourceDialog() }
        binding.btnRefresh.setOnClickListener { loadChannels(force = true) }
        binding.sourceDropdown.setOnLongClickListener {
            val src = currentSource
            if (src != null && src.custom) confirmDeleteSource(src)
            true
        }

        // pilih sumber terakhir
        val lastId = repo.getLastSourceId()
        currentSource = sources.find { it.id == lastId } ?: sources.first()
        binding.sourceDropdown.setText(currentSource!!.label, false)

        loadChannels()
    }

    private fun setupSourceDropdown() {
        val labels = sources.map { it.label }
        val ddAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        binding.sourceDropdown.setAdapter(ddAdapter)
        binding.sourceDropdown.setOnItemClickListener { _, _, position, _ ->
            currentSource = sources[position]
            repo.setLastSourceId(currentSource!!.id)
            loadChannels()
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                filterChannels(q.orEmpty())
                return true
            }
        })
    }

    private fun filterChannels(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allChannels
        else allChannels.filter { it.name.lowercase().contains(q) || (it.group?.lowercase()?.contains(q) == true) }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadChannels(force: Boolean = false) {
        val src = currentSource ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.searchView.setQuery("", false)
        lifecycleScope.launch {
            try {
                allChannels = repo.loadChannels(src)
                ChannelHolder.channels = allChannels
                adapter.submitList(allChannels)
                binding.emptyView.visibility = if (allChannels.isEmpty()) View.VISIBLE else View.GONE
                if (allChannels.isEmpty()) binding.emptyView.text = getString(R.string.empty_channels)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal load playlist: ${e.message}", Toast.LENGTH_LONG).show()
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = getString(R.string.load_failed)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun openPlayer(channel: Channel) {
        val list = adapter.currentList
        ChannelHolder.channels = list
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_INDEX, list.indexOf(channel).coerceAtLeast(0))
        startActivity(intent)
    }

    private fun showAddSourceDialog() {
        val ctx = this
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val inputName = TextInputEditText(ctx).apply { hint = "Nama (mis. Playlist Saya)" }
        val inputUrl = TextInputEditText(ctx).apply { hint = "URL M3U (https://...)" }
        layout.addView(inputName)
        layout.addView(inputUrl)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Tambah Playlist M3U")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val label = inputName.text.toString().trim().ifEmpty { "Playlist Custom" }
                val url = inputUrl.text.toString().trim()
                if (url.isEmpty() || !url.startsWith("http")) {
                    Toast.makeText(ctx, "URL tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repo.addCustomSource(label, url)
                refreshSources()
                Toast.makeText(ctx, "Playlist ditambahkan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteSource(src: PlaylistSource) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus playlist?")
            .setMessage("\"${src.label}\" akan dihapus dari daftar.")
            .setPositiveButton("Hapus") { _, _ ->
                repo.removeCustomSource(src)
                refreshSources()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refreshSources() {
        sources = repo.getSources()
        val labels = sources.map { it.label }
        binding.sourceDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        currentSource = sources.find { it.id == repo.getLastSourceId() } ?: sources.first()
        binding.sourceDropdown.setText(currentSource!!.label, false)
        setupSourceDropdown()
        loadChannels()
    }
}

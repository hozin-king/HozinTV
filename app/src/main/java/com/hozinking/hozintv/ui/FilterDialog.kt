package com.hozinking.hozintv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.hozinking.hozintv.R
import com.hozinking.hozintv.data.M3uParser
import com.hozinking.hozintv.data.PlaylistSource
import com.hozinking.hozintv.databinding.DialogFilterBinding

/**
 * Dialog filter ala BitTV dengan 2 tab:
 * - "Region": daftar playlist + kode region di kanan. Kode diambil data-driven
 *   dari pola URL countries/XX.m3u (bukan hardcode); kosong bila tidak ada.
 *   Kelola tambah/hapus URL ada di dialog Pengaturan (gear).
 * - "Category": "Semua" + daftar distinct group-title dari channel yang
 *   ter-load, dengan jumlah channel di kanan sebagai info jujur dari data.
 */
class FilterDialog(
    private val activity: AppCompatActivity,
    private val sources: List<PlaylistSource>,
    private val currentSourceId: String,
    private val categories: List<Pair<String, Int>>,
    private val currentCategory: String?,
    private val listener: Listener
) {

    interface Listener {
        fun onRegionSelected(source: PlaylistSource)
        fun onCategorySelected(category: String?)
    }

    private sealed interface Row {
        data class Region(val source: PlaylistSource, val code: String?, val selected: Boolean) : Row
        data class Category(val name: String?, val count: Int, val selected: Boolean) : Row
    }

    fun show() {
        val binding = DialogFilterBinding.inflate(LayoutInflater.from(activity))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()

        val adapter = FilterAdapter(
            onClick = { row ->
                when (row) {
                    is Row.Region -> {
                        dialog.dismiss()
                        listener.onRegionSelected(row.source)
                    }
                    is Row.Category -> {
                        dialog.dismiss()
                        listener.onCategorySelected(row.name)
                    }
                }
            }
        )
        binding.filterList.layoutManager = LinearLayoutManager(activity)
        binding.filterList.adapter = adapter

        fun refresh(tab: Int) {
            adapter.submitList(
                if (tab == 0) {
                    sources.map { s ->
                        Row.Region(s, M3uParser.extractRegionCode(s.url), s.id == currentSourceId)
                    }
                } else {
                    listOf(Row.Category(null, categories.sumOf { it.second }, currentCategory == null)) +
                        categories.map { (name, count) ->
                            Row.Category(name, count, name == currentCategory)
                        }
                }
            )
        }

        binding.filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = refresh(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.btnFilterCancel.setOnClickListener { dialog.dismiss() }

        refresh(0)
        dialog.show()
    }

    private class FilterAdapter(
        private val onClick: (Row) -> Unit
    ) : RecyclerView.Adapter<FilterAdapter.VH>() {

        private var rows: List<Row> = emptyList()

        fun submitList(list: List<Row>) {
            rows = list
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.filterItemName)
            val meta: TextView = v.findViewById(R.id.filterItemMeta)
            val check: ImageView = v.findViewById(R.id.filterItemCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_filter, parent, false)
            return VH(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val row = rows[position]
            when (row) {
                is Row.Region -> {
                    h.name.text = row.source.label
                    h.meta.text = row.code ?: ""
                    h.check.visibility = if (row.selected) View.VISIBLE else View.GONE
                }
                is Row.Category -> {
                    h.name.text = row.name ?: h.itemView.context.getString(R.string.all_categories)
                    h.meta.text = "${row.count}"
                    h.check.visibility = if (row.selected) View.VISIBLE else View.GONE
                }
            }
            h.itemView.setOnClickListener { onClick(row) }
        }
    }
}

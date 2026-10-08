package com.hozinking.hozintv.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.transform.RoundedCornersTransformation
import com.hozinking.hozintv.R
import com.hozinking.hozintv.databinding.ItemChannelBinding
import com.hozinking.hozintv.model.Channel

class ChannelAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(a: Channel, b: Channel) = a.url == b.url
            override fun areContentsTheSame(a: Channel, b: Channel) = a == b
        }
    }

    private var selectedUrl: String? = null

    fun setSelected(url: String?) {
        selectedUrl = url
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ch: Channel) {
            binding.channelName.text = ch.name
            binding.channelGroup.text = ch.group ?: ""
            val radiusPx = binding.root.resources.displayMetrics.density * 12f
            val logo = ch.logo
            if (logo != null && logo.startsWith("data:image")) {
                // Logo base64 ala BitTV: decode manual (batalkan request Coil sebelumnya)
                binding.channelLogo.dispose()
                try {
                    val bytes = android.util.Base64.decode(
                        logo.substringAfter("base64,"), android.util.Base64.DEFAULT
                    )
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) binding.channelLogo.setImageBitmap(bmp)
                    else binding.channelLogo.setImageResource(R.drawable.ic_tv)
                } catch (_: Exception) {
                    binding.channelLogo.setImageResource(R.drawable.ic_tv)
                }
            } else {
                binding.channelLogo.load(logo) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(radiusPx))
                    placeholder(R.drawable.ic_tv)
                    error(R.drawable.ic_tv)
                }
            }
            val selected = ch.url == selectedUrl
            binding.rowContent.setBackgroundColor(
                if (selected) ContextCompat.getColor(binding.root.context, R.color.row_selected)
                else Color.TRANSPARENT
            )
            binding.root.setOnClickListener { onClick(ch) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}

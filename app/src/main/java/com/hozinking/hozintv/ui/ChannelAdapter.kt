package com.hozinking.hozintv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
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

    inner class VH(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ch: Channel) {
            binding.channelName.text = ch.name
            binding.channelGroup.text = ch.group ?: ""
            binding.channelLogo.load(ch.logo) {
                crossfade(true)
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
            }
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
            binding.root.setOnClickListener { onClick(ch) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}

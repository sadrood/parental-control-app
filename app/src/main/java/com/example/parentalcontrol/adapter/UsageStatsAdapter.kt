package com.example.parentalcontrol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.parentalcontrol.databinding.ItemUsageStatBinding
import com.example.parentalcontrol.model.AppUsageInfo

class UsageStatsAdapter(private val items: List<AppUsageInfo>) : 
    RecyclerView.Adapter<UsageStatsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemUsageStatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUsageStatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvAppName.text = item.appName
        holder.binding.tvUsageTime.text = formatTime(item.usageTime)
    }

    override fun getItemCount() = items.size

    private fun formatTime(millis: Long): String {
        val minutes = millis / 1000 / 60
        return if (minutes >= 60) {
            "${minutes / 60}小时 ${minutes % 60}分钟"
        } else {
            "$minutes 分钟"
        }
    }
}

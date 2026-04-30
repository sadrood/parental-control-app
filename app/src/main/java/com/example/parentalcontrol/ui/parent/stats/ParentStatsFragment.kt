package com.example.parentalcontrol.ui.parent.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentStatsBinding
import com.example.parentalcontrol.model.AppUsage

class ParentStatsFragment : Fragment() {

    private var _binding: FragmentParentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var usageStatsViewModel: UsageStatsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        usageStatsViewModel = ViewModelProvider(requireActivity())[UsageStatsViewModel::class.java]

        observeData()
    }

    private fun observeData() {
        usageStatsViewModel.todayUsageTime.observe(viewLifecycleOwner) { totalMs ->
            val hours = totalMs / 3600000.0
            val minutes = (totalMs % 3600000) / 60000

            if (hours >= 1) {
                binding.tvTodayTotal.text = String.format("%.1f", hours)
                binding.tvTodayUnit.text = "小时"
            } else {
                binding.tvTodayTotal.text = "$minutes"
                binding.tvTodayUnit.text = "分钟"
            }
        }

        usageStatsViewModel.appUsageStats.observe(viewLifecycleOwner) { apps ->
            updateAppRanking(apps)
        }
    }

    private fun updateAppRanking(apps: List<AppUsage>) {
        val container = binding.llAppRanking
        container.removeAllViews()

        if (apps.isEmpty()) {
            val tv = TextView(requireContext())
            tv.text = "暂无使用数据"
            tv.setTextColor(resources.getColor(R.color.text_hint, null))
            tv.textSize = 14f
            container.addView(tv)
            return
        }

        val pm = requireContext().packageManager
        val colors = intArrayOf(
            resources.getColor(R.color.danger, null),
            resources.getColor(R.color.primary, null),
            resources.getColor(R.color.mint, null),
            resources.getColor(R.color.warning, null)
        )

        apps.take(10).forEachIndexed { index, app ->
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
            } catch (e: Exception) {
                app.packageName
            }
            val itemView = createRankingItemView(index + 1, appName, app.usageTime, colors[index % colors.size])
            container.addView(itemView)
        }
    }

    private fun createRankingItemView(rank: Int, appName: String, usageMs: Long, color: Int): View {
        val itemView = View.inflate(requireContext(), R.layout.item_app_ranking, null)

        val tvRank = itemView.findViewById<TextView>(R.id.tvRank)
        val tvAppName = itemView.findViewById<TextView>(R.id.tvAppName)
        val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        val progressBar = itemView.findViewById<ProgressBar>(R.id.progressBar)

        tvRank.text = "$rank"
        tvAppName.text = appName

        val hours = usageMs / 3600000
        val minutes = (usageMs % 3600000) / 60000
        tvTime.text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        progressBar.progressDrawable?.setTint(color)
        progressBar.progress = 100 - rank * 10

        return itemView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

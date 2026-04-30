package com.example.parentalcontrol.ui.parent.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentParentStatsBinding
import com.example.parentalcontrol.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentStatsFragment : Fragment() {

    private var _binding: FragmentParentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
        settingsManager = SettingsManager(requireContext())

        observeData()
    }

    private fun observeData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val todayTotal = repository.getTodayTotalUsage()
            val hours = todayTotal / 3600000.0
            val minutes = (todayTotal % 3600000) / 60000

            withContext(Dispatchers.Main) {
                if (hours >= 1) {
                    binding.tvTodayTotal.text = String.format("%.1f", hours)
                    binding.tvTodayUnit.text = "小时"
                } else {
                    binding.tvTodayTotal.text = "$minutes"
                    binding.tvTodayUnit.text = "分钟"
                }
            }
        }

        lifecycleScope.launch {
            repository.getDailyAppUsage().collect { usageList ->
                updateAppRanking(usageList)
            }
        }
    }

    private fun updateAppRanking(usageList: List<com.example.parentalcontrol.data.entity.DailyAppUsage>) {
        val container = binding.llAppRanking
        container.removeAllViews()

        if (usageList.isEmpty()) {
            val tv = android.widget.TextView(requireContext())
            tv.text = "暂无使用数据"
            tv.setTextColor(resources.getColor(R.color.text_hint, null))
            tv.textSize = 14f
            container.addView(tv)
            return
        }

        val colors = intArrayOf(
            resources.getColor(R.color.danger, null),
            resources.getColor(R.color.primary, null),
            resources.getColor(R.color.mint, null),
            resources.getColor(R.color.warning, null)
        )

        usageList.take(4).forEachIndexed { index, record ->
            val itemView = createRankingItemView(index + 1, record.appName, record.usageTimeMs, colors[index % colors.size])
            container.addView(itemView)
        }
    }

    private fun createRankingItemView(rank: Int, appName: String, usageMs: Long, color: Int): View {
        val dp = requireContext().resources.displayMetrics.density
        val itemView = View.inflate(requireContext(), R.layout.item_app_ranking, null)

        val tvRank = itemView.findViewById<android.widget.TextView>(R.id.tvRank)
        val tvAppName = itemView.findViewById<android.widget.TextView>(R.id.tvAppName)
        val tvTime = itemView.findViewById<android.widget.TextView>(R.id.tvTime)
        val progressBar = itemView.findViewById<android.widget.ProgressBar>(R.id.progressBar)

        tvRank.text = "$rank"
        tvAppName.text = appName

        val hours = usageMs / 3600000
        val minutes = (usageMs % 3600000) / 60000
        tvTime.text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        progressBar.progressDrawable?.setTint(color)
        progressBar.progress = 100 - rank * 20

        return itemView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

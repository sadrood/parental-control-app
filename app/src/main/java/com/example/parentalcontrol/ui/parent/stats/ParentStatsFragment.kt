package com.example.parentalcontrol.ui.parent.stats

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentStatsBinding
import com.example.parentalcontrol.model.AppUsage
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.util.LogUtil
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentStatsFragment : BaseFragment<FragmentParentStatsBinding>() {

    companion object {
        private const val TAG = "ParentStats"
        private val CHART_COLORS = intArrayOf(
            Color.rgb(66, 165, 245),
            Color.rgb(102, 187, 106),
            Color.rgb(255, 202, 40),
            Color.rgb(239, 83, 80),
            Color.rgb(255, 112, 67),
            Color.rgb(171, 71, 188),
            Color.rgb(38, 166, 154),
            Color.rgb(141, 110, 99)
        )
    }

    private lateinit var usageStatsViewModel: UsageStatsViewModel
    private var isWeekly = true

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentParentStatsBinding {
        return FragmentParentStatsBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            usageStatsViewModel = ViewModelProvider(requireActivity())[UsageStatsViewModel::class.java]
            setupToggleGroup()
            setupSwipeRefresh()
            setupBarChart()
            setupPieChart()
            observeData()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupToggleGroup() {
        safeRun {
            togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                isWeekly = (checkedId == R.id.btnThisWeek)
                observeData()
            }
        }
    }

    private fun setupSwipeRefresh() {
        safeRun {
            swipeRefresh.setOnRefreshListener {
                usageStatsViewModel.refresh()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupBarChart() {
        safeRun {
            barChart.apply {
                description.isEnabled = false
                setFitBars(true)
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setPinchZoom(false)
                setScaleEnabled(false)
                legend.textColor = resources.getColor(R.color.text_secondary, null)
                legend.textSize = 11f
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    textColor = resources.getColor(R.color.text_hint, null)
                    textSize = 10f
                }
                axisLeft.apply {
                    textColor = resources.getColor(R.color.text_hint, null)
                    textSize = 10f
                    axisMinimum = 0f
                    setDrawGridLines(true)
                    gridColor = resources.getColor(R.color.divider, null)
                }
                axisRight.isEnabled = false
            }
        }
    }

    private fun setupPieChart() {
        safeRun {
            pieChart.apply {
                description.isEnabled = false
                setUsePercentValues(true)
                setDrawHoleEnabled(true)
                setHoleColor(Color.WHITE)
                setTransparentCircleRadius(61f)
                setHoleRadius(50f)
                setDrawCenterText(true)
                centerText = ""
                setDrawEntryLabels(false)
                legend.textColor = resources.getColor(R.color.text_secondary, null)
                legend.textSize = 11f
                legend.formSize = 10f
                rotationAngle = 0f
                isRotationEnabled = true
                setEntryLabelColor(resources.getColor(R.color.text_secondary, null))
                setEntryLabelTextSize(10f)
                animateY(1000)
            }
        }
    }

    private fun observeData() {
        usageStatsViewModel.todayUsageTime.observeSafe { totalMs ->
            safeRun {
                val hours = totalMs / 3600000.0
                val minutes = (totalMs % 3600000) / 60000
                if (hours >= 1) {
                    tvTodayTotal.text = String.format("%.1f", hours)
                    tvTodayUnit.text = "小时"
                } else {
                    tvTodayTotal.text = "$minutes"
                    tvTodayUnit.text = "分钟"
                }
            }
        }

        usageStatsViewModel.appUsageStats.observeSafe { apps ->
            updateAppRanking(apps)
            updateBarChart(apps)
            updatePieChart(apps)
        }
    }

    private var isBarChartUpdating = false
    private var isPieChartUpdating = false

    private fun updateBarChart(apps: List<AppUsage>) {
        if (isBarChartUpdating || _binding == null) return
        isBarChartUpdating = true
        safeRun {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 从真实数据生成柱状图：按天聚合app使用时长
                    val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                    val entries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()

                    // 基于传入的真实数据生成最近7天的柱状图
                    if (isWeekly) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val cal = java.util.Calendar.getInstance()
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -6)
                        for (i in 0..6) {
                            val dateStr = sdf.format(cal.time)
                            val dayUsage = apps
                                .filter { it.usageTime > 0 }
                                .sumOf { it.usageTime } / 60000f / 7f // 均分到每天
                            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                            val labelIndex = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - 2
                            entries.add(BarEntry(i.toFloat(), dayUsage))
                            labels.add(dayNames[if (labelIndex in 0..6) labelIndex else 0])
                            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                    } else {
                        for (i in 0..6) {
                            entries.add(BarEntry(i.toFloat(), apps.sumOf { it.usageTime } / 60000f / 7f))
                            labels.add("${i + 1}日")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        safeRun {
                            // 防抖：只更新数据不重建布局
                            val barData = barChart.barData
                            if (barData != null && barData.dataSetCount > 0) {
                                val set = barData.getDataSetByIndex(0) as? BarDataSet
                                if (set != null && set.values.size == entries.size) {
                                    // 更新已有数据值，避免重新创建
                                    for (j in entries.indices) {
                                        set.values[j] = entries[j]
                                    }
                                    barData.notifyDataChanged()
                                    barChart.notifyDataSetChanged()
                                    barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                                } else {
                                    createNewBarChart(entries, labels)
                                }
                            } else {
                                createNewBarChart(entries, labels)
                            }
                            barChart.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "更新柱状图失败", e)
                } finally {
                    isBarChartUpdating = false
                }
            }
        }
    }

    private fun createNewBarChart(entries: List<BarEntry>, labels: List<String>) {
        safeRun {
            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            val dataSet = BarDataSet(entries, "使用时长(分钟)").apply {
                color = resources.getColor(R.color.primary, null)
                valueTextColor = resources.getColor(R.color.text_secondary, null)
                valueTextSize = 10f
                setDrawValues(false)
            }
            barChart.data = BarData(dataSet)
        }
    }

    private fun updatePieChart(apps: List<AppUsage>) {
        if (isPieChartUpdating || _binding == null) return
        isPieChartUpdating = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pm = requireContext().packageManager
                val categoryTime = mutableMapOf<String, Long>()

                for (app in apps) {
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
                    } catch (e: Exception) { app.packageName }
                    val category = categorizePackage(app.packageName, appName)
                    categoryTime[category] = (categoryTime[category] ?: 0) + app.usageTime
                }

                val entries = categoryTime.map { (category, time) ->
                    PieEntry(time / 60000f, category)
                }

                withContext(Dispatchers.Main) {
                    safeRun {
                        if (entries.isEmpty()) return@safeRun
                        // 防抖：只更新数据，不重建 PieDataSet
                        val pieData = pieChart.data
                        if (pieData != null && pieData.dataSet != null) {
                            val set = pieData.dataSet as? PieDataSet
                            if (set != null) {
                                set.values = entries
                                set.colors = CHART_COLORS.take(entries.size).toList()
                                pieData.notifyDataChanged()
                                pieChart.notifyDataSetChanged()
                            } else {
                                createNewPieChart(entries)
                            }
                        } else {
                            createNewPieChart(entries)
                        }
                        pieChart.invalidate()
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "更新饼图失败", e)
            } finally {
                isPieChartUpdating = false
            }
        }
    }

    private fun createNewPieChart(entries: List<PieEntry>) {
        safeRun {
            val dataSet = PieDataSet(entries, "").apply {
                colors = CHART_COLORS.toList()
                valueFormatter = PercentFormatter()
                valueTextSize = 11f
                valueTextColor = Color.WHITE
                sliceSpace = 3f
                selectionShift = 5f
            }
            pieChart.data = PieData(dataSet)
        }
    }

    private var lastRankingAppCount = 0

    private fun updateAppRanking(apps: List<AppUsage>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pm = requireContext().packageManager
                val total = apps.sumOf { it.usageTime }.coerceAtLeast(1)
                val sortedApps = apps.sortedByDescending { it.usageTime }.take(10)

                withContext(Dispatchers.Main) {
                    safeRun {
                        if (apps.isEmpty()) {
                            if (lastRankingAppCount != 0) {
                                llAppRanking.removeAllViews()
                                llAppRanking.addView(createEmptyView("暂无数据，正在同步"))
                                lastRankingAppCount = 0
                            }
                            return@safeRun
                        }

                        // 防抖：只有当应用数量变化时才重建视图
                        if (sortedApps.size != lastRankingAppCount) {
                            lastRankingAppCount = sortedApps.size
                            llAppRanking.removeAllViews()
                            sortedApps.forEachIndexed { index, app ->
                                val appName = try {
                                    pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
                                } catch (e: Exception) { app.packageName }
                                val pct = (app.usageTime * 100 / total).toInt()
                                llAppRanking.addView(createRankingItemView(index + 1, appName, app.usageTime, pct, false))
                            }
                        } else {
                            // 只更新文字，不重建视图
                            for (i in 0 until llAppRanking.childCount) {
                                val child = llAppRanking.getChildAt(i) as? LinearLayout ?: continue
                                val app = sortedApps.getOrNull(i) ?: continue
                                for (j in 0 until child.childCount) {
                                    val tv = child.getChildAt(j) as? TextView ?: continue
                                    when (j) {
                                        1 -> { // appName
                                            val appName = try {
                                                pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
                                            } catch (e: Exception) { app.packageName }
                                            tv.text = appName
                                        }
                                        2 -> { // usageTime
                                            val hours = app.usageTime / 3600000
                                            val minutes = (app.usageTime % 3600000) / 60000
                                            tv.text = if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
                                        }
                                        3 -> { // percent
                                            val pct = (app.usageTime * 100 / total).toInt()
                                            tv.text = "$pct%"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "更新应用排行失败", e)
            }
        }
    }

    private fun createRankingItemView(rank: Int, appName: String, usageMs: Long, percent: Int, isDisabled: Boolean): View {
        val density = resources.displayMetrics.density
        val rootView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * density).toInt()
            }
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(resources.getColor(R.color.card_bg, null))
        }

        rootView.addView(TextView(requireContext()).apply {
            text = "$rank"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(if (rank <= 3) R.color.primary else R.color.text_hint, null))
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        rootView.addView(TextView(requireContext()).apply {
            text = appName
            textSize = 14f
            setTextColor(resources.getColor(if (isDisabled) R.color.red else R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (12 * density).toInt() }
        })

        val hours = usageMs / 3600000
        val minutes = (usageMs % 3600000) / 60000
        rootView.addView(TextView(requireContext()).apply {
            text = if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_hint, null))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = (8 * density).toInt() }
        })

        rootView.addView(TextView(requireContext()).apply {
            text = "$percent%"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.primary, null))
        })

        return rootView
    }

    private fun createEmptyView(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_hint, null))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * resources.displayMetrics.density).toInt())
        }
    }

    private fun categorizePackage(packageName: String, appName: String): String {
        val lower = "$packageName $appName".lowercase()
        return when {
            lower.contains("game") || lower.contains("video") || lower.contains("player") || lower.contains("bilibili") || lower.contains("youtube") || lower.contains("tencent") -> "娱乐"
            lower.contains("edu") || lower.contains("study") || lower.contains("homework") || lower.contains("learn") || lower.contains("class") -> "学习"
            lower.contains("wechat") || lower.contains("qq") || lower.contains("tiktok") || lower.contains("douyin") || lower.contains("xiaohongshu") || lower.contains("weibo") -> "社交"
            else -> "其他"
        }
    }
}

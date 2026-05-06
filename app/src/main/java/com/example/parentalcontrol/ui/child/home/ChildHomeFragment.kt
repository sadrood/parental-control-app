package com.example.parentalcontrol.ui.child.home

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.entity.AppRule
import com.example.parentalcontrol.data.entity.DailyAppUsage
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentChildHomeBinding
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.ui.parent.stats.UsageStatsViewModel
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager
import com.example.parentalcontrol.util.TimeChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChildHomeFragment : BaseFragment<FragmentChildHomeBinding>() {

    companion object {
        private const val TAG = "ChildHome"
    }

    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var usageStatsViewModel: UsageStatsViewModel

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChildHomeBinding {
        return FragmentChildHomeBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val db = AppDatabase.getDatabase(requireContext())
            repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
            settingsManager = SettingsManager(requireContext())
            usageStatsViewModel = ViewModelProvider(requireActivity())[UsageStatsViewModel::class.java]

            setupClickListeners()
            observeData()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupClickListeners() {
        safeRun {
            btnContactParent.setOnClickListener {
                val phone = settingsManager.parentPhone
                if (phone.isNotEmpty()) {
                    startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") })
                } else {
                    Toast.makeText(context, "家长未设置联系电话", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeData() {
        usageStatsViewModel.remainingTime.observeSafe { remaining ->
            safeRun {
                tvRemainingTime.text = "$remaining"
                tvRemainingMinutes.text = "分钟"
                val limitHours = (usageStatsViewModel.dailyLimit.value ?: 60) / 60
                tvTimeTip.text = getString(R.string.parent_rule, limitHours)

                if (remaining < 60) {
                    tvRemainingTime.setTextColor(resources.getColor(R.color.red, null))
                    tvRemainingMinutes.setTextColor(resources.getColor(R.color.red, null))
                } else {
                    tvRemainingTime.setTextColor(resources.getColor(R.color.primary, null))
                    tvRemainingMinutes.setTextColor(resources.getColor(R.color.primary, null))
                }
            }
        }

        repository.enabledTimeRules.collectSafe { rules ->
            safeRun {
                val isAllowed = TimeChecker.isCurrentlyAllowed(rules)
                tvPeriodStatus.text = if (isAllowed) "当前时段：可用" else "当前时段：不可用"
                tvPeriodStatus.setTextColor(resources.getColor(if (isAllowed) R.color.green else R.color.red, null))
                dotStatus.background = resources.getDrawable(if (isAllowed) R.drawable.circle_green else R.drawable.circle_red, null)
            }
        }

        repository.allAppRules.collectSafe { allRules ->
            lifecycleScope.launch(Dispatchers.IO) {
                val blockedPackages = allRules.filter { it.isBlocked }.map { it.packageName }.toSet()
                val whitelisted = allRules.filter { it.isWhitelisted }

                withContext(Dispatchers.Main) {
                    if (whitelisted.isNotEmpty()) {
                        populateAppGrid(whitelisted, blockedPackages)
                    } else {
                        repository.getDailyAppUsage().collectSafe { topApps ->
                            if (topApps.isNotEmpty()) {
                                populateAppGridFromUsage(topApps.take(6), blockedPackages)
                            } else {
                                safeRun { llAppGrid.removeAllViews() }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun populateAppGrid(apps: List<AppRule>, blockedPackages: Set<String>) {
        safeRun {
            llAppGrid.removeAllViews()
            val pm = requireContext().packageManager
            for (chunk in apps.chunked(3)) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                chunk.forEach {
                    val isBlocked = blockedPackages.contains(it.packageName)
                    row.addView(createAppCard(it.appName, it.packageName, pm, isBlocked))
                }
                llAppGrid.addView(row)
            }
        }
    }

    private fun populateAppGridFromUsage(apps: List<DailyAppUsage>, blockedPackages: Set<String>) {
        safeRun {
            llAppGrid.removeAllViews()
            val pm = requireContext().packageManager
            for (chunk in apps.chunked(3)) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                chunk.forEach {
                    val isBlocked = blockedPackages.contains(it.packageName)
                    row.addView(createAppCard(it.appName, it.packageName, pm, isBlocked))
                }
                llAppGrid.addView(row)
            }
        }
    }

    private fun createAppCard(appName: String, packageName: String, pm: PackageManager, isBlocked: Boolean): View {
        val density = resources.displayMetrics.density
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            alpha = if (isBlocked) 0.4f else 1f
        }

        val icon = try { pm.getApplicationIcon(packageName) } catch (e: Exception) { null }

        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((80 * density).toInt(), (80 * density).toInt())
            radius = (16 * density).toFloat()
            cardElevation = (2 * density).toFloat()
            setCardBackgroundColor(resources.getColor(R.color.card_bg, null))
            addView(ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (icon != null) setImageDrawable(icon) else setImageResource(R.drawable.ic_launcher_simple)
            })

            if (isBlocked) {
                setOnClickListener {
                    Toast.makeText(context, getString(R.string.app_disabled_by_parent), Toast.LENGTH_SHORT).show()
                }
            }
        }

        container.addView(cardView)
        container.addView(TextView(requireContext()).apply {
            text = appName
            textSize = 13f
            setTextColor(resources.getColor(if (isBlocked) R.color.text_hint else R.color.text_primary, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * density).toInt() }
        })

        return container
    }
}

package com.example.parentalcontrol.ui.child.home

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.entity.AppRule
import com.example.parentalcontrol.data.entity.DailyAppUsage
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentChildHomeBinding
import com.example.parentalcontrol.ui.parent.stats.UsageStatsViewModel
import com.example.parentalcontrol.util.SettingsManager
import com.example.parentalcontrol.util.TimeChecker
import kotlinx.coroutines.launch

class ChildHomeFragment : Fragment() {

    private var _binding: FragmentChildHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var usageStatsViewModel: UsageStatsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChildHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
        settingsManager = SettingsManager(requireContext())
        usageStatsViewModel = ViewModelProvider(requireActivity())[UsageStatsViewModel::class.java]

        setupViews()
        setupClickListeners()
        observeData()
    }

    private fun setupViews() {
        // 更新提示信息，包含孩子名字
        binding.tvTimeTip.text = "你好，${settingsManager.childName}！"
    }

    private fun setupClickListeners() {
        binding.btnContactParent.setOnClickListener {
            val phone = settingsManager.parentPhone
            if (phone.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(context, "家长未设置联系电话", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        usageStatsViewModel.remainingTime.observe(viewLifecycleOwner) { remaining ->
            binding.tvRemainingTime.text = "$remaining"
            binding.tvRemainingMinutes.text = "分钟"

            val tipText = getString(R.string.arrange_time, remaining)
            binding.tvTimeTip.text = tipText
        }

        lifecycleScope.launch {
            repository.enabledTimeRules.collect { rules ->
                val isAllowed = TimeChecker.isCurrentlyAllowed(rules)
                if (isAllowed) {
                    binding.tvPeriodStatus.text = "当前时段：可用"
                    binding.tvPeriodStatus.setTextColor(resources.getColor(R.color.mint, null))
                    binding.dotStatus.setBackgroundColor(resources.getColor(R.color.mint, null))
                } else {
                    binding.tvPeriodStatus.text = "当前时段：不可用"
                    binding.tvPeriodStatus.setTextColor(resources.getColor(R.color.danger, null))
                    binding.dotStatus.setBackgroundColor(resources.getColor(R.color.danger, null))
                }
            }
        }

        lifecycleScope.launch {
            repository.whitelistedApps.collect { whitelisted ->
                if (whitelisted.isNotEmpty()) {
                    populateAppGrid(whitelisted)
                } else {
                    repository.getDailyAppUsage().collect { topApps ->
                        if (topApps.isNotEmpty()) {
                            populateAppGridFromUsage(topApps.take(6))
                        } else {
                            binding.llAppGrid.removeAllViews()
                        }
                    }
                }
            }
        }
    }

    private fun populateAppGrid(apps: List<AppRule>) {
        binding.llAppGrid.removeAllViews()
        val pm = requireContext().packageManager
        val chunked = apps.chunked(3)
        for (chunk in chunked) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (app in chunk) {
                val card = createAppCard(app.appName, app.packageName, pm)
                row.addView(card)
            }
            binding.llAppGrid.addView(row)
        }
    }

    private fun populateAppGridFromUsage(apps: List<DailyAppUsage>) {
        binding.llAppGrid.removeAllViews()
        val pm = requireContext().packageManager
        val chunked = apps.chunked(3)
        for (chunk in chunked) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (app in chunk) {
                val card = createAppCard(app.appName, app.packageName, pm)
                row.addView(card)
            }
            binding.llAppGrid.addView(row)
        }
    }

    private fun createAppCard(appName: String, packageName: String, pm: PackageManager): View {
        val density = resources.displayMetrics.density

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val icon = try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((80 * density).toInt(), (80 * density).toInt())
            radius = (16 * density).toFloat()
            cardElevation = (2 * density).toFloat()
            setCardBackgroundColor(resources.getColor(R.color.card_bg, null))
            addView(ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (icon != null) {
                    setImageDrawable(icon)
                } else {
                    setImageResource(R.drawable.ic_launcher_simple)
                }
            })
        }

        container.addView(cardView)

        container.addView(TextView(requireContext()).apply {
            text = appName
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt() }
        })

        return container
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

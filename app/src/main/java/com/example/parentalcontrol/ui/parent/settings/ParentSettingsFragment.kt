package com.example.parentalcontrol.ui.parent.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentParentSettingsBinding
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentSettingsFragment : BaseFragment<FragmentParentSettingsBinding>() {

    companion object {
        private const val TAG = "ParentSettings"
    }

    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager
    private var nightModeEnabled = false

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentParentSettingsBinding {
        return FragmentParentSettingsBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val db = AppDatabase.getDatabase(requireContext())
            repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
            settingsManager = SettingsManager(requireContext())
            setupViews()
            setupClickListeners()
            observeData()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupViews() {
        safeRun {
            val limitHours = settingsManager.dailyTimeLimit / 60f
            sliderTimeLimit.value = limitHours.coerceIn(0.5f, 12f)
            tvTimeLimitValue.text = getString(R.string.daily_time_limit_hours, settingsManager.dailyTimeLimit / 60)

            nightModeEnabled = settingsManager.webFilterEnabled
            switchNightMode.isChecked = nightModeEnabled
            tvNightTimeHint.visibility = if (nightModeEnabled) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        safeRun {
            sliderTimeLimit.addOnChangeListener { _, value, _ ->
                val minutes = (value * 60).toInt()
                tvTimeLimitValue.text = getString(R.string.daily_time_limit_hours, minutes / 60)
            }

            sliderTimeLimit.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    settingsManager.dailyTimeLimit = (slider.value * 60).toInt()
                    Toast.makeText(context, getString(R.string.rule_effective), Toast.LENGTH_SHORT).show()
                }
            })

            switchNightMode.setOnCheckedChangeListener { _, checked ->
                nightModeEnabled = checked
                tvNightTimeHint.visibility = if (checked) View.VISIBLE else View.GONE
                settingsManager.webFilterEnabled = checked
                if (checked) {
                    Toast.makeText(context, getString(R.string.night_mode_enabled), Toast.LENGTH_SHORT).show()
                }
            }

            btnSave.setOnClickListener { saveSettings() }
            btnReset.setOnClickListener { resetSettings() }
        }
    }

    private fun saveSettings() {
        safeRun {
            settingsManager.dailyTimeLimit = (sliderTimeLimit.value * 60).toInt()
            settingsManager.webFilterEnabled = switchNightMode.isChecked
            Toast.makeText(context, getString(R.string.rule_effective), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetSettings() {
        safeRun {
            settingsManager.dailyTimeLimit = 60
            settingsManager.webFilterEnabled = true
            sliderTimeLimit.value = 1f
            tvTimeLimitValue.text = getString(R.string.daily_time_limit_hours, 1)
            switchNightMode.isChecked = true
            tvNightTimeHint.visibility = View.VISIBLE
            Toast.makeText(context, "已重置为默认设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        repository.allAppRules.collectSafe { rules ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val pm = requireContext().packageManager
                    val appItems = rules.map { rule ->
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(rule.packageName, 0)).toString()
                        } catch (e: Exception) { rule.packageName }
                        Triple(appName, rule.packageName, rule.isBlocked)
                    }

                    withContext(Dispatchers.Main) {
                        safeRun {
                            if (appItems.isEmpty()) {
                                tvNoApps.visibility = View.VISIBLE
                                cardAppList.visibility = View.GONE
                            } else {
                                tvNoApps.visibility = View.GONE
                                cardAppList.visibility = View.VISIBLE
                                llAppList.removeAllViews()
                                val density = resources.displayMetrics.density
                                appItems.forEachIndexed { index, (name, pkg, blocked) ->
                                    llAppList.addView(createAppItemView(name, pkg, blocked))
                                    if (index < appItems.size - 1) {
                                        llAppList.addView(View(requireContext()).apply {
                                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                                                leftMargin = (60 * density).toInt()
                                            }
                                            setBackgroundColor(resources.getColor(R.color.divider, null))
                                        })
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "加载应用规则失败", e)
                }
            }
        }
    }

    private fun createAppItemView(appName: String, packageName: String, isBlocked: Boolean): View {
        val density = resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            alpha = if (isBlocked) 0.5f else 1f

            addView(TextView(requireContext()).apply {
                text = appName
                textSize = 15f
                setTextColor(resources.getColor(if (isBlocked) R.color.text_hint else R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(MaterialSwitch(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                isChecked = !isBlocked
                setOnCheckedChangeListener { _, checked ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val rule = repository.getAppRule(packageName)
                            if (rule != null) {
                                repository.updateAppRule(rule.copy(isBlocked = !checked))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, getString(R.string.rule_effective), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            LogUtil.e(TAG, "更新应用规则失败", e)
                        }
                    }
                }
            })
        }
    }
}

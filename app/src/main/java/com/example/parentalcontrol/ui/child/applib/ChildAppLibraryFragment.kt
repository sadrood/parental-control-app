package com.example.parentalcontrol.ui.child.applib

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppListRepository
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentChildAppLibraryBinding
import com.example.parentalcontrol.model.AppInfo
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.util.LogUtil
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChildAppLibraryFragment : BaseFragment<FragmentChildAppLibraryBinding>() {

    companion object {
        private const val TAG = "ChildAppLib"
    }

    private lateinit var appListRepository: AppListRepository
    private lateinit var appRepository: AppRepository
    private var allApps: List<AppInfo> = emptyList()
    private var blockedPackages: Set<String> = emptySet()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChildAppLibraryBinding {
        return FragmentChildAppLibraryBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            appListRepository = AppListRepository(requireContext())
            val db = AppDatabase.getDatabase(requireContext())
            appRepository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
            setupSearch()
            loadApps()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupSearch() {
        safeRun {
            etSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString() ?: ""
                    if (query.isEmpty()) displayApps(allApps)
                    else displayApps(appListRepository.searchApps(query))
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apps = appListRepository.getInstalledApps()
                val rules = withContext(Dispatchers.Main) {
                    var result: List<com.example.parentalcontrol.data.entity.AppRule> = emptyList()
                    appRepository.allAppRules.collectSafe { r -> result = r }
                    result
                }
                // Use a different approach to get blocked packages
                withContext(Dispatchers.Main) {
                    allApps = apps
                    blockedPackages = emptySet()
                    displayApps(apps)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "加载应用列表失败", e)
            }
        }
        // Fall back to direct flow collection
        appRepository.allAppRules.collectSafe { rules ->
            blockedPackages = rules.filter { it.isBlocked }.map { it.packageName }.toSet()
            if (allApps.isNotEmpty()) {
                displayApps(allApps)
            }
        }
    }

    private fun displayApps(apps: List<AppInfo>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val available = mutableListOf<AppInfo>()
            val disabled = mutableListOf<AppInfo>()

            for (app in apps) {
                if (blockedPackages.contains(app.packageName)) {
                    disabled.add(app)
                } else {
                    available.add(app)
                }
            }

            withContext(Dispatchers.Main) {
                safeRun {
                    llAvailableApps.removeAllViews()
                    llDisabledApps.removeAllViews()
                    tvAvailableCount.text = "${available.size}"
                    tvDisabledCount.text = "${disabled.size}"

                    if (available.isEmpty()) {
                        llAvailableApps.addView(createEmptyView("暂无可用的应用"))
                    } else {
                        available.forEach { app ->
                            llAvailableApps.addView(createAppItemView(app, false))
                        }
                    }

                    if (disabled.isEmpty()) {
                        llDisabledApps.addView(createEmptyView("暂无被禁用的应用"))
                    } else {
                        disabled.forEach { app ->
                            llDisabledApps.addView(createAppItemView(app, true))
                        }
                    }
                }
            }
        }
    }

    private fun createAppItemView(app: AppInfo, isDisabled: Boolean): View {
        val density = resources.displayMetrics.density
        val cardView = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * density).toInt()
            }
            radius = (16 * density).toFloat()
            cardElevation = (1 * density).toFloat()
            setCardBackgroundColor(resources.getColor(R.color.card_bg, null))
            alpha = if (isDisabled) 0.5f else 1f
            isClickable = !isDisabled

            if (isDisabled) {
                setOnClickListener {
                    Toast.makeText(context, getString(R.string.app_disabled_by_parent), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
        }

        row.addView(ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (app.icon != null) setImageDrawable(app.icon) else setImageResource(R.drawable.ic_launcher_simple)
            alpha = if (isDisabled) 0.5f else 1f
        })

        val textContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (14 * density).toInt()
            }
            addView(TextView(requireContext()).apply {
                text = app.appName
                textSize = 15f
                setTextColor(resources.getColor(if (isDisabled) R.color.text_hint else R.color.text_primary, null))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(requireContext()).apply {
                text = app.packageName
                textSize = 11f
                setTextColor(resources.getColor(R.color.text_hint, null))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (3 * density).toInt()
                }
            })
        }
        row.addView(textContainer)

        row.addView(TextView(requireContext()).apply {
            text = if (isDisabled) getString(R.string.disabled_label) else getString(R.string.available_label)
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(if (isDisabled) R.color.red else R.color.green, null))
            background = resources.getDrawable(
                if (isDisabled) R.drawable.bg_chip_red_light else R.drawable.bg_chip_green_light, null
            )
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
        })

        cardView.addView(row)
        return cardView
    }

    private fun createEmptyView(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_hint, null))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (100 * resources.displayMetrics.density).toInt())
        }
    }
}

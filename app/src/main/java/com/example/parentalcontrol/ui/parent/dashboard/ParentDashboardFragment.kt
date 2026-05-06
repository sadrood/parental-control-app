package com.example.parentalcontrol.ui.parent.dashboard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.data.repository.RepositoryResult
import com.example.parentalcontrol.databinding.FragmentParentDashboardBinding
import com.example.parentalcontrol.model.AppUsage
import com.example.parentalcontrol.network.ApiClient
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.network.SafeApiCaller
import com.example.parentalcontrol.network.model.LockScreenRequest
import com.example.parentalcontrol.network.model.BaseResponse
import com.example.parentalcontrol.receiver.AdminReceiver
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.ui.parent.stats.UsageStatsViewModel
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentDashboardFragment : BaseFragment<FragmentParentDashboardBinding>() {

    companion object {
        private const val TAG = "ParentDash"
    }

    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var authManager: AuthManager
    private lateinit var usageStatsViewModel: UsageStatsViewModel

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentParentDashboardBinding {
        return FragmentParentDashboardBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val db = AppDatabase.getDatabase(requireContext())
            repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
            settingsManager = SettingsManager(requireContext())
            devicePolicyManager = requireActivity().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            adminComponent = ComponentName(requireContext(), AdminReceiver::class.java)
            authManager = AuthManager.getInstance(requireContext())
            usageStatsViewModel = ViewModelProvider(requireActivity())[UsageStatsViewModel::class.java]

            setupClickListeners()
            observeData()
            setupSwipeRefresh()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupClickListeners() {
        safeRun {
            btnLockScreen.setOnClickListener { sendLockCommand(true) }
            btnUnlock.setOnClickListener { sendLockCommand(false) }
            btnStudyMode.setOnClickListener { toggleStudyMode() }
            cardControlSettings.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_settings) }
            cardSecurityCenter.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_security) }
            tvViewDetails.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_stats) }
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

    private fun sendLockCommand(lock: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SafeApiCaller.call<BaseResponse>(TAG) {
                val api = ApiClient.getApiService()
                val parentId = authManager.getCurrentUserId() ?: throw IllegalStateException("未登录")
                if (lock) api.lockScreen(LockScreenRequest(parentId))
                else api.unlockScreen(LockScreenRequest(parentId))
            }
            withContext(Dispatchers.Main) {
                val msg = if (lock) "锁屏指令已发送" else "解锁指令已发送"
                Toast.makeText(context, if (result is RepositoryResult.Success) "${msg}到儿童端" else "发送失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleStudyMode() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(context, "请先开启辅助功能服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            settingsManager.studyMode = !settingsManager.studyMode
            Toast.makeText(context, if (settingsManager.studyMode) "学习模式已开启" else "学习模式已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        usageStatsViewModel.remainingTime.observeSafe { remaining ->
            safeRun {
                val hours = remaining / 60
                val minutes = remaining % 60
                tvRemainingTime.text = "$hours"
                tvRemainingMinutes.text = "$minutes"
                val limit = usageStatsViewModel.dailyLimit.value ?: 60
                val used = limit - remaining.toInt().coerceAtMost(limit)
                val pct = if (limit > 0) (used * 100 / limit) else 0
                progressRemaining.progress = pct
                tvUsedTime.text = "已用 ${if (used >= 60) "${used / 60}小时${used % 60}分钟" else "${used}分钟"}"

                when {
                    remaining <= 30 -> {
                        progressRemaining.progressDrawable?.setTint(resources.getColor(R.color.red, null))
                        tvRemainingTime.setTextColor(resources.getColor(R.color.red, null))
                    }
                    remaining <= 60 -> {
                        progressRemaining.progressDrawable?.setTint(resources.getColor(R.color.yellow, null))
                        tvRemainingTime.setTextColor(resources.getColor(R.color.yellow, null))
                    }
                    else -> {
                        progressRemaining.progressDrawable?.setTint(resources.getColor(R.color.green, null))
                        tvRemainingTime.setTextColor(resources.getColor(R.color.primary, null))
                    }
                }
            }
        }

        usageStatsViewModel.appUsageStats.observeSafe { apps ->
            if (apps.isNotEmpty()) {
                updateAppStats(apps)
            }
        }

        usageStatsViewModel.childOnlineStatus.observeSafe { online ->
            safeRun {
                dotOnlineStatus.background = resources.getDrawable(
                    if (online) R.drawable.circle_green else R.drawable.circle_red, null
                )
                tvOnlineStatus.text = if (online) getString(R.string.child_online_status) else getString(R.string.child_offline_status)
            }
        }
    }

    private fun updateAppStats(apps: List<AppUsage>) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pm = requireContext().packageManager
                val totalUsage = apps.sumOf { it.usageTime }.coerceAtLeast(1)
                var gameMs = 0L; var studyMs = 0L; var socialMs = 0L

                for (app in apps) {
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
                    } catch (e: Exception) { app.packageName }
                    when (categorizePackage(app.packageName, appName)) {
                        "游戏", "娱乐" -> gameMs += app.usageTime
                        "教育", "学习" -> studyMs += app.usageTime
                        "社交" -> socialMs += app.usageTime
                    }
                }

                val totalMs = totalUsage.coerceAtLeast(1)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    safeRun {
                        tvGameTime.text = formatDuration(gameMs)
                        tvStudyTime.text = formatDuration(studyMs)
                        tvSocialTime.text = formatDuration(socialMs)
                        tvGamePercent.text = "${(gameMs * 100 / totalMs).toInt()}%"
                        tvStudyPercent.text = "${(studyMs * 100 / totalMs).toInt()}%"
                        tvSocialPercent.text = "${(socialMs * 100 / totalMs).toInt()}%"
                        progressGame.progress = (gameMs * 100 / totalMs).toInt()
                        progressStudy.progress = (studyMs * 100 / totalMs).toInt()
                        progressSocial.progress = (socialMs * 100 / totalMs).toInt()
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "更新应用统计失败", e)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
    }

    private fun categorizePackage(packageName: String, appName: String): String {
        val lower = "$packageName $appName".lowercase()
        return when {
            lower.contains("game") || lower.contains("tencent") || lower.contains("video") || lower.contains("player") || lower.contains("bilibili") || lower.contains("youtube") -> "娱乐"
            lower.contains("edu") || lower.contains("study") || lower.contains("homework") || lower.contains("learn") || lower.contains("class") -> "学习"
            lower.contains("wechat") || lower.contains("qq") || lower.contains("tiktok") || lower.contains("douyin") || lower.contains("xiaohongshu") || lower.contains("weibo") -> "社交"
            else -> "其他"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val service = "${requireContext().packageName}/${requireContext().packageName}.service.AppControlService"
            val enabled = Settings.Secure.getInt(requireContext().contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            if (enabled == 1) {
                val settingValue = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                settingValue?.contains(service) == true
            } else false
        } catch (e: Exception) {
            LogUtil.e(TAG, "检查辅助功能失败", e)
            false
        }
    }
}

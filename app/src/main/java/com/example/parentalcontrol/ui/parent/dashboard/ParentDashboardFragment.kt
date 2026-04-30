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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentParentDashboardBinding
import com.example.parentalcontrol.model.AppUsage
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.receiver.AdminReceiver
import com.example.parentalcontrol.ui.parent.stats.UsageStatsViewModel
import com.example.parentalcontrol.util.SettingsManager
import kotlinx.coroutines.launch

class ParentDashboardFragment : Fragment() {

    private var _binding: FragmentParentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var authManager: AuthManager
    private lateinit var usageStatsViewModel: UsageStatsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
        settingsManager = SettingsManager(requireContext())
        devicePolicyManager = requireActivity().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(requireContext(), AdminReceiver::class.java)
        authManager = AuthManager.getInstance(requireContext())
        usageStatsViewModel = ViewModelProvider(requireActivity())[UsageStatsViewModel::class.java]

        setupViews()
        setupClickListeners()
        observeData()
    }

    private fun setupViews() {
        binding.tvChildName.text = "${settingsManager.childName}的手机"
    }

    private fun setupClickListeners() {
        binding.btnLockScreen.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val apiService = com.example.parentalcontrol.network.ApiClient.getApiService()
                    val parentId = authManager.getCurrentUserId() ?: return@launch
                    val response = apiService.lockScreen(
                        com.example.parentalcontrol.network.model.LockScreenRequest(parentId = parentId)
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(context, "锁屏指令已发送到儿童端", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "发送失败，请检查网络连接", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnUnlock.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val apiService = com.example.parentalcontrol.network.ApiClient.getApiService()
                    val parentId = authManager.getCurrentUserId() ?: return@launch
                    val response = apiService.unlockScreen(
                        com.example.parentalcontrol.network.model.LockScreenRequest(parentId = parentId)
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(context, "解锁指令已发送到儿童端", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "发送失败，请检查网络连接", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnStudyMode.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(context, "请先开启辅助功能服务", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                settingsManager.studyMode = !settingsManager.studyMode
                val msg = if (settingsManager.studyMode) "学习模式已开启" else "学习模式已关闭"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardControlSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }

        binding.cardSecurityCenter.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_security)
        }

        binding.tvViewDetails.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_stats)
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }
    }

    private fun observeData() {
        usageStatsViewModel.remainingTime.observe(viewLifecycleOwner) { remainingMinutes ->
            binding.tvRemainingTime.text = "$remainingMinutes"
            binding.tvRemainingUnit.text = "分钟"
        }

        usageStatsViewModel.appUsageStats.observe(viewLifecycleOwner) { apps ->
            if (apps.isNotEmpty()) {
                updateAppStatsFromUsageManager(apps)
            }
        }

        lifecycleScope.launch {
            repository.getDailyAppUsage().collect { usageList ->
                if (usageList.isNotEmpty()) {
                    updateAppStats(usageList)
                }
            }
        }
    }

    private fun updateAppStats(usageList: List<com.example.parentalcontrol.data.entity.DailyAppUsage>) {
        if (usageList.isEmpty()) {
            binding.tvGameTime.text = "0m"
            binding.tvStudyTime.text = "0m"
            binding.tvSocialTime.text = "0m"
            binding.progressGame.progress = 0
            binding.progressStudy.progress = 0
            binding.progressSocial.progress = 0
            return
        }

        val totalUsage = usageList.sumOf { it.usageTimeMs }.coerceAtLeast(1)

        val gameApps = usageList.filter { it.category == "游戏" || it.category == "娱乐" }
        val studyApps = usageList.filter { it.category == "教育" || it.category == "学习" }
        val socialApps = usageList.filter { it.category == "社交" }

        applyCategoryStats(gameApps.sumOf { it.usageTimeMs }, studyApps.sumOf { it.usageTimeMs }, socialApps.sumOf { it.usageTimeMs }, totalUsage)
    }

    private fun updateAppStatsFromUsageManager(apps: List<AppUsage>) {
        val pm = requireContext().packageManager
        val totalUsage = apps.sumOf { it.usageTime }.coerceAtLeast(1)

        var gameMs = 0L
        var studyMs = 0L
        var socialMs = 0L

        for (app in apps) {
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
            } catch (e: Exception) {
                app.packageName
            }
            val category = categorizePackage(app.packageName, appName)
            when (category) {
                "游戏", "娱乐" -> gameMs += app.usageTime
                "教育", "学习" -> studyMs += app.usageTime
                "社交" -> socialMs += app.usageTime
            }
        }

        applyCategoryStats(gameMs, studyMs, socialMs, totalUsage)
    }

    private fun applyCategoryStats(gameMs: Long, studyMs: Long, socialMs: Long, totalMs: Long) {
        val gameMinutes = gameMs / 60000
        val studyMinutes = studyMs / 60000
        val socialMinutes = socialMs / 60000

        binding.tvGameTime.text = "${gameMinutes}m"
        binding.tvStudyTime.text = "${studyMinutes}m"
        binding.tvSocialTime.text = "${socialMinutes}m"

        binding.progressGame.progress = ((gameMs * 100 / totalMs).toInt())
        binding.progressStudy.progress = ((studyMs * 100 / totalMs).toInt())
        binding.progressSocial.progress = ((socialMs * 100 / totalMs).toInt())
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

    private fun requestAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${requireContext().packageName}/${requireContext().packageName}.service.AppControlService"
        val enabled = Settings.Secure.getInt(requireContext().contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains(service) == true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

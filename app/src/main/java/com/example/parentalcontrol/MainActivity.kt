package com.example.parentalcontrol

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.parentalcontrol.databinding.ActivityMainBinding
import com.example.parentalcontrol.network.ApiClient
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.network.WebSocketManager
import com.example.parentalcontrol.receiver.AdminReceiver
import com.example.parentalcontrol.service.CloudSyncService
import com.example.parentalcontrol.service.MonitoringService
import com.example.parentalcontrol.util.ChildLogUtil
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: AuthManager
    private lateinit var webSocketManager: WebSocketManager
    private var devicePolicyManager: DevicePolicyManager? = null
    private var adminComponent: ComponentName? = null
    private var settingsManager: SettingsManager? = null
    private var currentRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 确保日志系统已初始化（已登录用户直接进入 MainActivity 的情况）
        ChildLogUtil.init(this)

        authManager = AuthManager.getInstance(this)
        if (!authManager.isLoggedIn()) {
            navigateToPairing()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webSocketManager = WebSocketManager.getInstance()
        currentRole = authManager.getCurrentUserRole()

        setupNavigation(currentRole)
        setupRoleGuard(currentRole)
        requestPermissionsForRole(currentRole)
        startServicesForRole(currentRole)
        connectWebSocket()
    }

    private fun navigateToPairing() {
        val intent = Intent(this, com.example.parentalcontrol.ui.auth.PairingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupNavigation(role: String?) {
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                ?: return
            val navController = navHostFragment.navController
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

            val startId = if (role == "child") R.id.childHomeFragment else R.id.parentDashboardFragment
            navGraph.setStartDestination(startId)
            navController.graph = navGraph

            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
            if (bottomNav != null) {
                if (role == "child") {
                    bottomNav.menu.clear()
                    bottomNav.inflateMenu(R.menu.bottom_nav_child)
                } else {
                    bottomNav.menu.clear()
                    bottomNav.inflateMenu(R.menu.bottom_nav_parent)
                }
                bottomNav.setupWithNavController(navController)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "设置导航失败", e)
        }
    }

    private fun setupRoleGuard(role: String?) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: return
        val navController = navHostFragment.navController

        val childDestinations = setOf(R.id.childHomeFragment, R.id.childAppLibraryFragment)
        val parentDestinations = setOf(R.id.parentDashboardFragment, R.id.parentSettingsFragment, R.id.parentLocationFragment, R.id.parentStatsFragment, R.id.parentProfileFragment, R.id.parentSecurityFragment)

        navController.addOnDestinationChangedListener { _, destination: NavDestination, _ ->
            if (role == "child" && destination.id !in childDestinations) {
                LogUtil.w(TAG, "儿童端试图访问非授权页面: ${destination.label}")
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.childHomeFragment, true)
                    .build()
                navController.navigate(R.id.childHomeFragment, null, navOptions)
            } else if (role == "parent" && destination.id !in parentDestinations) {
                LogUtil.w(TAG, "家长端试图访问非授权页面: ${destination.label}")
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.parentDashboardFragment, true)
                    .build()
                navController.navigate(R.id.parentDashboardFragment, null, navOptions)
            }
        }
    }

    private fun requestPermissionsForRole(role: String?) {
        if (role == "child") {
            requestChildPermissions()
        } else {
            requestParentPermissions()
        }
    }

    private fun requestChildPermissions() {
        try {
            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            adminComponent = ComponentName(this, AdminReceiver::class.java)
            settingsManager = SettingsManager(this)

            // 持久化权限状态检查：只有首次启动且权限未授予时才弹窗
            val prefs = getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
            val isFirstPermissionRequest = prefs.getBoolean("first_permission_request", true)

            if (devicePolicyManager?.isAdminActive(adminComponent!!) == false && isFirstPermissionRequest) {
                requestAdminPermission()
            }

            val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            if (mode != android.app.AppOpsManager.MODE_ALLOWED && isFirstPermissionRequest) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this) && isFirstPermissionRequest) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }

            // 标记权限请求已触发过，下次不再重复弹出
            if (isFirstPermissionRequest) {
                prefs.edit().putBoolean("first_permission_request", false).apply()
            }

            val childPermissions = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                childPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestRuntimePermissions(childPermissions.toTypedArray())
        } catch (e: Exception) {
            LogUtil.e(TAG, "申请儿童权限失败", e)
        }
    }

    private fun requestParentPermissions() {
        requestRuntimePermissions(emptyArray())
    }

    private fun requestRuntimePermissions(permissions: Array<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed, REQUEST_CODE_PERMISSIONS)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val denied = permissions.filterIndexed { index, _ -> grantResults[index] != PackageManager.PERMISSION_GRANTED }
            if (denied.isNotEmpty() && currentRole == "child") {
                Toast.makeText(this, "部分权限未授予，定位上报和联系家长功能可能受限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestAdminPermission() {
        try {
            val component = adminComponent ?: return
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogUtil.e(TAG, "申请设备管理员失败", e)
        }
    }

    private fun startServicesForRole(role: String?) {
        try {
            startCloudSyncService()
            if (role == "child") {
                startMonitoringService()
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "启动服务失败", e)
        }
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun startCloudSyncService() {
        val serviceIntent = Intent(this, CloudSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun connectWebSocket() {
        try {
            val userId = authManager.getCurrentUserId() ?: return
            val token = ApiClient.authToken ?: return
            webSocketManager.connect(ApiClient.WS_URL, userId)
            webSocketManager.bindUser(userId, token)
            LogUtil.d(TAG, "WebSocket 已连接并绑定用户")
        } catch (e: Exception) {
            LogUtil.e(TAG, "WebSocket 连接失败", e)
        }
    }

    override fun onDestroy() {
        try {
            if (::webSocketManager.isInitialized && webSocketManager.isConnectionActive()) {
                webSocketManager.disconnect()
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "断开WebSocket失败", e)
        }
        super.onDestroy()
    }
}

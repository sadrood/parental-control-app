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
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.parentalcontrol.databinding.ActivityMainBinding
import com.example.parentalcontrol.network.ApiClient
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.network.WebSocketManager
import com.example.parentalcontrol.receiver.AdminReceiver
import com.example.parentalcontrol.service.CloudSyncService
import com.example.parentalcontrol.service.MonitoringService
import com.example.parentalcontrol.util.SettingsManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: AuthManager
    private lateinit var webSocketManager: WebSocketManager

    private var devicePolicyManager: DevicePolicyManager? = null
    private var adminComponent: ComponentName? = null
    private var settingsManager: SettingsManager? = null

    private val REQUEST_CODE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager.getInstance(this)
        if (!authManager.isLoggedIn()) {
            navigateToPairing()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webSocketManager = WebSocketManager.getInstance()

        val role = authManager.getCurrentUserRole()
        setupNavigation(role)
        requestPermissionsForRole(role)
        startServicesForRole(role)
        connectWebSocket()
    }

    private fun navigateToPairing() {
        val intent = Intent(this, com.example.parentalcontrol.ui.auth.PairingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupNavigation(role: String?) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
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
    }

    private fun requestPermissionsForRole(role: String?) {
        if (role == "child") {
            requestChildPermissions()
        } else {
            requestParentPermissions()
        }
    }

    private fun requestChildPermissions() {
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        settingsManager = SettingsManager(this)

        if (!devicePolicyManager!!.isAdminActive(adminComponent!!)) {
            requestAdminPermission()
        }

        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        val childPermissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            childPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestRuntimePermissions(childPermissions.toTypedArray())
    }

    private fun requestParentPermissions() {
        val parentPermissions = arrayOf<String>()
        requestRuntimePermissions(parentPermissions)
    }

    private fun requestRuntimePermissions(permissions: Array<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsNeeded = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsNeeded.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsNeeded, REQUEST_CODE_PERMISSIONS)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val role = authManager.getCurrentUserRole()
            val denied = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty() && role == "child") {
                Toast.makeText(this, "部分权限未授予，定位上报和联系家长功能可能受限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent!!)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
        startActivity(intent)
    }

    private fun startServicesForRole(role: String?) {
        startCloudSyncService()
        if (role == "child") {
            startMonitoringService()
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
        val userId = authManager.getCurrentUserId() ?: return
        val token = ApiClient.authToken ?: return

        webSocketManager.connect(ApiClient.WS_URL, userId)
        webSocketManager.bindUser(userId, token)
        Log.d("MainActivity", "WebSocket 已连接并绑定用户")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocketManager.isInitialized && webSocketManager.isConnectionActive()) {
            webSocketManager.disconnect()
            Log.d("MainActivity", "WebSocket 已断开连接")
        }
    }
}

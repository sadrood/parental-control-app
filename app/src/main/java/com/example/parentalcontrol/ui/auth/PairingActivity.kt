package com.example.parentalcontrol.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.MainActivity
import com.example.parentalcontrol.R
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.network.WebSocketManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * 配对登录页
 * 流程：
 * 1. 选择角色（家长/儿童）
 * 2. 匿名登录
 * 3. 家长端：显示配对码 → 将此码告诉儿童 → 点击"进入应用"
 * 4. 儿童端：输入家长显示的6位配对码 → 点击"绑定设备" → 调用 POST /api/pairing/link → 进入主页
 */
class PairingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PairingActivity"
    }

    private lateinit var authManager: AuthManager
    private lateinit var webSocketManager: WebSocketManager

    private lateinit var btnParent: MaterialCardView
    private lateinit var btnChild: MaterialCardView
    private lateinit var layoutPairInput: View
    private lateinit var layoutPairDisplay: View
    private lateinit var etPairingCode: EditText
    private lateinit var btnPair: MaterialButton
    private lateinit var tvPairingCode: TextView
    private lateinit var tvWaitingPair: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEnterMain: MaterialButton

    private var selectedRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        authManager = AuthManager.getInstance(this)
        webSocketManager = WebSocketManager.getInstance()

        initViews()

        if (authManager.isLoggedIn() && authManager.isPaired.value) {
            enterMainActivity()
            return
        }

        if (authManager.isLoggedIn()) {
            val role = authManager.getCurrentUserRole()
            if (role != null) {
                onRoleSelected(role)
            }
        }
    }

    private fun initViews() {
        btnParent = findViewById(R.id.btnParent)
        btnChild = findViewById(R.id.btnChild)
        layoutPairInput = findViewById(R.id.layoutPairInput)
        layoutPairDisplay = findViewById(R.id.layoutPairDisplay)
        etPairingCode = findViewById(R.id.etPairingCode)
        btnPair = findViewById(R.id.btnPair)
        tvPairingCode = findViewById(R.id.tvPairingCode)
        tvWaitingPair = findViewById(R.id.tvWaitingPair)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        btnEnterMain = findViewById(R.id.btnEnterMain)

        btnParent.setOnClickListener { onRoleSelected("parent") }
        btnChild.setOnClickListener { onRoleSelected("child") }
        btnPair.setOnClickListener { onChildPairClicked() }
        btnEnterMain.setOnClickListener { enterMainActivity() }
    }

    private fun onRoleSelected(role: String) {
        selectedRole = role
        btnParent.alpha = if (role == "parent") 1f else 0.5f
        btnChild.alpha = if (role == "child") 1f else 0.5f

        if (authManager.isLoggedIn() && authManager.getCurrentUserRole() == role) {
            showRoleUI(role)
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            val result = authManager.anonymousLogin(role)
            showLoading(false)

            if (result.isSuccess) {
                val loginResult = result.getOrNull() ?: return@launch
                Log.d(TAG, "登录成功: ${loginResult.userId}, role=${loginResult.role}")

                webSocketManager.connect(com.example.parentalcontrol.network.ApiClient.WS_URL, loginResult.userId)
                webSocketManager.bindUser(loginResult.userId, loginResult.token)

                showRoleUI(role)
            } else {
                val error = result.exceptionOrNull()?.message ?: "登录失败"
                showStatus(error)
                Toast.makeText(this@PairingActivity, "登录失败: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRoleUI(role: String) {
        if (role == "parent") {
            layoutPairInput.visibility = View.GONE
            layoutPairDisplay.visibility = View.VISIBLE
            tvWaitingPair.visibility = View.GONE
            btnEnterMain.visibility = View.VISIBLE

            val code = authManager.pairingCode.value
            if (code != null) {
                tvPairingCode.text = code
                tvWaitingPair.text = "将此配对码告诉儿童设备"
                tvWaitingPair.visibility = View.VISIBLE
            } else {
                showStatus("未获取到配对码，请重试")
            }
        } else {
            layoutPairInput.visibility = View.VISIBLE
            layoutPairDisplay.visibility = View.GONE
        }
    }

    private fun onChildPairClicked() {
        val code = etPairingCode.text.toString().trim()

        if (code.length != 6) {
            showStatus("请输入6位配对码")
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            val result = authManager.linkWithCode(code)
            showLoading(false)

            if (result.isSuccess) {
                Toast.makeText(this@PairingActivity, "配对成功！", Toast.LENGTH_SHORT).show()
                enterMainActivity()
            } else {
                val error = result.exceptionOrNull()?.message ?: "配对失败"
                showStatus(error)
                Toast.makeText(this@PairingActivity, "配对失败: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enterMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        tvStatus.visibility = View.GONE
    }

    private fun showStatus(message: String) {
        tvStatus.text = message
        tvStatus.visibility = View.VISIBLE
    }
}

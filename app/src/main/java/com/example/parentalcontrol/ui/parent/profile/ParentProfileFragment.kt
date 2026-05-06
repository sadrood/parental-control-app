package com.example.parentalcontrol.ui.parent.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.parentalcontrol.databinding.FragmentParentProfileBinding
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager

class ParentProfileFragment : BaseFragment<FragmentParentProfileBinding>() {

    companion object {
        private const val TAG = "ParentProfile"
    }

    private lateinit var settingsManager: SettingsManager

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentParentProfileBinding {
        return FragmentParentProfileBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            settingsManager = SettingsManager(requireContext())
            setupViews()
            setupClickListeners()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupViews() {
        safeRun {
            val parentName = settingsManager.parentName
            if (parentName.isNotEmpty()) tvParentName.text = parentName
            val phone = settingsManager.parentPhone
            tvParentPhone.text = when {
                phone.isNotEmpty() && phone.length >= 7 -> "${phone.substring(0, 3)} **** ${phone.substring(phone.length - 4)}"
                phone.isNotEmpty() -> phone
                else -> "未绑定手机号"
            }
        }
    }

    private fun setupClickListeners() {
        safeRun {
            btnBack.setOnClickListener { requireActivity().onBackPressed() }
            cardPersonalInfo.setOnClickListener { Toast.makeText(context, "个人资料编辑", Toast.LENGTH_SHORT).show() }
            cardAccountSecurity.setOnClickListener { Toast.makeText(context, "账号安全设置", Toast.LENGTH_SHORT).show() }
            cardNotifications.setOnClickListener { Toast.makeText(context, "消息通知设置", Toast.LENGTH_SHORT).show() }
            cardSubscription.setOnClickListener { Toast.makeText(context, "订阅管理", Toast.LENGTH_SHORT).show() }
            btnLogout.setOnClickListener {
                Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
    }
}

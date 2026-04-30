package com.example.parentalcontrol.ui.parent.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentProfileBinding
import com.example.parentalcontrol.util.SettingsManager

class ParentProfileFragment : Fragment() {

    private var _binding: FragmentParentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        val parentName = settingsManager.parentName
        if (parentName.isNotEmpty()) {
            binding.tvParentName.text = parentName
        }
        val phone = settingsManager.parentPhone
        if (phone.isNotEmpty() && phone.length >= 7) {
            binding.tvParentPhone.text = "${phone.substring(0, 3)} **** ${phone.substring(phone.length - 4)}"
        } else if (phone.isNotEmpty()) {
            binding.tvParentPhone.text = phone
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.cardPersonalInfo.setOnClickListener {
            Toast.makeText(context, "个人资料编辑", Toast.LENGTH_SHORT).show()
        }

        binding.cardAccountSecurity.setOnClickListener {
            Toast.makeText(context, "账号安全设置", Toast.LENGTH_SHORT).show()
        }

        binding.cardNotifications.setOnClickListener {
            Toast.makeText(context, "消息通知设置", Toast.LENGTH_SHORT).show()
        }

        binding.cardSubscription.setOnClickListener {
            Toast.makeText(context, "订阅管理", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.parentalcontrol.ui.parent.security

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentSecurityBinding
import com.example.parentalcontrol.util.SettingsManager

class ParentSecurityFragment : Fragment() {

    private var _binding: FragmentParentSecurityBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        binding.switchAntiUninstall.isChecked = settingsManager.antiUninstall
        binding.switchAntiRoot.isChecked = settingsManager.antiRoot
        binding.switchTimeTamper.isChecked = settingsManager.timeTamperProof
        binding.switchScreenshotAudit.isChecked = settingsManager.screenshotAudit

        binding.tvSecurityScore.text = "${settingsManager.securityScore}"
        updateSecurityStatus()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.switchAntiUninstall.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.antiUninstall = isChecked
            updateSecurityStatus()
        }

        binding.switchAntiRoot.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.antiRoot = isChecked
            updateSecurityStatus()
        }

        binding.switchTimeTamper.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.timeTamperProof = isChecked
            updateSecurityStatus()
        }

        binding.switchScreenshotAudit.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.screenshotAudit = isChecked
            updateSecurityStatus()
        }

        binding.cardFingerprint.setOnClickListener {
            Toast.makeText(context, "指纹锁功能", Toast.LENGTH_SHORT).show()
        }

        binding.cardRemoteReset.setOnClickListener {
            Toast.makeText(context, "远程重置功能", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSecurityStatus() {
        var score = 100
        if (!settingsManager.antiUninstall) score -= 15
        if (!settingsManager.antiRoot) score -= 10
        if (!settingsManager.timeTamperProof) score -= 15
        if (!settingsManager.screenshotAudit) score -= 5

        settingsManager.securityScore = score
        binding.tvSecurityScore.text = "$score"

        // 更新状态文字
        binding.tvAntiUninstallStatus.text = if (settingsManager.antiUninstall) getString(R.string.enabled) else getString(R.string.disabled)
        binding.tvAntiUninstallStatus.setTextColor(if (settingsManager.antiUninstall) resources.getColor(R.color.success, null) else resources.getColor(R.color.text_hint, null))

        binding.tvAntiRootStatus.text = getString(R.string.safe)
        binding.tvAntiRootStatus.setTextColor(resources.getColor(R.color.success, null))

        binding.tvTimeTamperStatus.text = if (settingsManager.timeTamperProof) getString(R.string.enabled) else getString(R.string.disabled)
        binding.tvTimeTamperStatus.setTextColor(if (settingsManager.timeTamperProof) resources.getColor(R.color.success, null) else resources.getColor(R.color.text_hint, null))

        binding.tvScreenshotStatus.text = if (settingsManager.screenshotAudit) getString(R.string.enabled) else getString(R.string.disabled)
        binding.tvScreenshotStatus.setTextColor(if (settingsManager.screenshotAudit) resources.getColor(R.color.success, null) else resources.getColor(R.color.text_hint, null))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

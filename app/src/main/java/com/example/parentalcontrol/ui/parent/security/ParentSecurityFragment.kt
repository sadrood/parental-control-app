package com.example.parentalcontrol.ui.parent.security

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentSecurityBinding
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager

class ParentSecurityFragment : BaseFragment<FragmentParentSecurityBinding>() {

    companion object {
        private const val TAG = "ParentSecurity"
    }

    private lateinit var settingsManager: SettingsManager

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentParentSecurityBinding {
        return FragmentParentSecurityBinding.inflate(inflater, container, false)
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
            switchAntiUninstall.isChecked = settingsManager.antiUninstall
            switchAntiRoot.isChecked = settingsManager.antiRoot
            switchTimeTamper.isChecked = settingsManager.timeTamperProof
            switchScreenshotAudit.isChecked = settingsManager.screenshotAudit
            tvSecurityScore.text = "${settingsManager.securityScore}"
            updateSecurityStatus()
        }
    }

    private fun setupClickListeners() {
        safeRun {
            btnBack.setOnClickListener { requireActivity().onBackPressed() }
            switchAntiUninstall.setOnCheckedChangeListener { _, _ -> settingsManager.antiUninstall = switchAntiUninstall.isChecked; updateSecurityStatus() }
            switchAntiRoot.setOnCheckedChangeListener { _, _ -> settingsManager.antiRoot = switchAntiRoot.isChecked; updateSecurityStatus() }
            switchTimeTamper.setOnCheckedChangeListener { _, _ -> settingsManager.timeTamperProof = switchTimeTamper.isChecked; updateSecurityStatus() }
            switchScreenshotAudit.setOnCheckedChangeListener { _, _ -> settingsManager.screenshotAudit = switchScreenshotAudit.isChecked; updateSecurityStatus() }
            cardFingerprint.setOnClickListener { Toast.makeText(context, "指纹锁功能", Toast.LENGTH_SHORT).show() }
            cardRemoteReset.setOnClickListener { Toast.makeText(context, "远程重置功能", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun updateSecurityStatus() {
        safeRun {
            var score = 100
            if (!settingsManager.antiUninstall) score -= 15
            if (!settingsManager.antiRoot) score -= 10
            if (!settingsManager.timeTamperProof) score -= 15
            if (!settingsManager.screenshotAudit) score -= 5
            settingsManager.securityScore = score
            tvSecurityScore.text = "$score"
            tvAntiUninstallStatus.text = if (settingsManager.antiUninstall) getString(R.string.enabled) else getString(R.string.disabled)
            tvAntiUninstallStatus.setTextColor(resources.getColor(if (settingsManager.antiUninstall) R.color.success else R.color.text_hint, null))
            tvAntiRootStatus.text = getString(R.string.safe)
            tvAntiRootStatus.setTextColor(resources.getColor(R.color.success, null))
            tvTimeTamperStatus.text = if (settingsManager.timeTamperProof) getString(R.string.enabled) else getString(R.string.disabled)
            tvTimeTamperStatus.setTextColor(resources.getColor(if (settingsManager.timeTamperProof) R.color.success else R.color.text_hint, null))
            tvScreenshotStatus.text = if (settingsManager.screenshotAudit) getString(R.string.enabled) else getString(R.string.disabled)
            tvScreenshotStatus.setTextColor(resources.getColor(if (settingsManager.screenshotAudit) R.color.success else R.color.text_hint, null))
        }
    }
}

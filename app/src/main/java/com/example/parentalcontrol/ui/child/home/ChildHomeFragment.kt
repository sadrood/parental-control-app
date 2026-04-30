package com.example.parentalcontrol.ui.child.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentChildHomeBinding
import com.example.parentalcontrol.util.SettingsManager
import com.example.parentalcontrol.util.TimeChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChildHomeFragment : Fragment() {

    private var _binding: FragmentChildHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChildHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())
        settingsManager = SettingsManager(requireContext())

        setupViews()
        setupClickListeners()
        observeData()
    }

    private fun setupViews() {
        // 更新提示信息，包含孩子名字
        binding.tvTimeTip.text = "你好，${settingsManager.childName}！"
    }

    private fun setupClickListeners() {
        binding.btnContactParent.setOnClickListener {
            val phone = settingsManager.parentPhone
            if (phone.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(context, "家长未设置联系电话", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val todayTotal = repository.getTodayTotalUsage()
            val dailyLimit = settingsManager.dailyTimeLimit
            val remaining = TimeChecker.getRemainingTimeToday(dailyLimit, todayTotal)

            withContext(Dispatchers.Main) {
                binding.tvRemainingTime.text = "$remaining"
                binding.tvRemainingMinutes.text = "分钟"

                val tipText = getString(R.string.arrange_time, remaining)
                binding.tvTimeTip.text = tipText
            }
        }

        lifecycleScope.launch {
            repository.enabledTimeRules.collect { rules ->
                val isAllowed = TimeChecker.isCurrentlyAllowed(rules)
                if (isAllowed) {
                    binding.tvPeriodStatus.text = "当前时段：可用"
                    binding.tvPeriodStatus.setTextColor(resources.getColor(R.color.mint, null))
                    binding.dotStatus.setBackgroundColor(resources.getColor(R.color.mint, null))
                } else {
                    binding.tvPeriodStatus.text = "当前时段：不可用"
                    binding.tvPeriodStatus.setTextColor(resources.getColor(R.color.danger, null))
                    binding.dotStatus.setBackgroundColor(resources.getColor(R.color.danger, null))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

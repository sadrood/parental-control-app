package com.example.parentalcontrol.ui.parent.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.entity.AppRule
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentParentSettingsBinding
import com.example.parentalcontrol.util.SettingsManager
import kotlinx.coroutines.launch

class ParentSettingsFragment : Fragment() {

    private var _binding: FragmentParentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentSettingsBinding.inflate(inflater, container, false)
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
        // 时长设置
        val timeLimits = arrayOf("30分钟", "1小时", "1.5小时", "2小时", "3小时")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, timeLimits)
        binding.spinnerTimeLimit.adapter = adapter

        val currentLimit = settingsManager.dailyTimeLimit
        val index = when (currentLimit) {
            30 -> 0
            60 -> 1
            90 -> 2
            120 -> 3
            180 -> 4
            else -> 1
        }
        binding.spinnerTimeLimit.setSelection(index)

        binding.switchWeekdayWeekend.isChecked = settingsManager.diffWeekdayWeekend
        binding.switchWebFilter.isChecked = settingsManager.webFilterEnabled
        binding.switchYouthMode.isChecked = settingsManager.youthModeEnabled
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnReset.setOnClickListener {
            resetSettings()
        }
    }

    private fun saveSettings() {
        val selectedLimit = when (binding.spinnerTimeLimit.selectedItemPosition) {
            0 -> 30
            1 -> 60
            2 -> 90
            3 -> 120
            4 -> 180
            else -> 60
        }

        settingsManager.dailyTimeLimit = selectedLimit
        settingsManager.diffWeekdayWeekend = binding.switchWeekdayWeekend.isChecked
        settingsManager.webFilterEnabled = binding.switchWebFilter.isChecked
        settingsManager.youthModeEnabled = binding.switchYouthMode.isChecked

        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun resetSettings() {
        settingsManager.dailyTimeLimit = 60
        settingsManager.diffWeekdayWeekend = false
        settingsManager.webFilterEnabled = true
        settingsManager.youthModeEnabled = true
        settingsManager.weekendTimeLimit = 120

        setupViews()
        Toast.makeText(context, "已重置为默认设置", Toast.LENGTH_SHORT).show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            repository.allAppRules.collect { rules ->
                updateAppList(rules)
            }
        }
    }

    private fun updateAppList(rules: List<AppRule>) {
        if (rules.isEmpty()) {
            // 显示空状态或示例数据
            binding.tvNoApps.visibility = View.VISIBLE
            binding.llAppList.visibility = View.GONE
        } else {
            binding.tvNoApps.visibility = View.GONE
            binding.llAppList.visibility = View.VISIBLE
            // 实际项目中使用 RecyclerView 适配器
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

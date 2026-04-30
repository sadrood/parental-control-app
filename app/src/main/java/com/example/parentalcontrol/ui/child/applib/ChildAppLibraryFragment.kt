package com.example.parentalcontrol.ui.child.applib

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.repository.AppRepository
import com.example.parentalcontrol.databinding.FragmentChildAppLibraryBinding

class ChildAppLibraryFragment : Fragment() {

    private var _binding: FragmentChildAppLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChildAppLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = AppRepository(db.appRuleDao(), db.timeRuleDao(), db.usageRecordDao(), db.securityEventDao())

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        val categories = listOf("全部", "学习", "益智", "阅读", "艺术", "音乐")
        val chipAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)

        // Setup category chips
        val chipGroup = binding.chipGroup
        categories.forEach { category ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = category
                isCheckable = true
                setChipBackgroundColorResource(R.color.divider)
                setTextColor(resources.getColor(R.color.text_secondary, null))
            }
            chipGroup.addView(chip)
            if (category == "全部") {
                chip.isChecked = true
                chip.setChipBackgroundColorResource(R.color.primary)
                chip.setTextColor(resources.getColor(R.color.white, null))
            }

            chip.setOnClickListener {
                chipGroup.clearCheck()
                chip.isChecked = true
                // Reset all chip colors
                for (i in 0 until chipGroup.childCount) {
                    val c = chipGroup.getChildAt(i) as com.google.android.material.chip.Chip
                    c.setChipBackgroundColorResource(R.color.divider)
                    c.setTextColor(resources.getColor(R.color.text_secondary, null))
                }
                chip.setChipBackgroundColorResource(R.color.primary)
                chip.setTextColor(resources.getColor(R.color.white, null))
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text.toString()
            if (query.isNotEmpty()) {
                Toast.makeText(context, "搜索: $query", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

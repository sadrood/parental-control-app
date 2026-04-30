package com.example.parentalcontrol.ui.child.applib

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.repository.AppListRepository
import com.example.parentalcontrol.databinding.FragmentChildAppLibraryBinding
import com.example.parentalcontrol.model.AppInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChildAppLibraryFragment : Fragment() {

    private var _binding: FragmentChildAppLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var appListRepository: AppListRepository
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChildAppLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appListRepository = AppListRepository(requireContext())

        setupViews()
        setupClickListeners()
        loadApps()
    }

    private fun setupViews() {
        val categories = listOf("全部", "学习", "益智", "阅读", "艺术", "音乐")
        val chipGroup = binding.chipGroup
        categories.forEachIndexed { index, category ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = category
                isCheckable = true
                setChipBackgroundColorResource(R.color.divider)
                setTextColor(resources.getColor(R.color.text_secondary, null))
            }
            chipGroup.addView(chip)
            if (index == 0) {
                chip.isChecked = true
                chip.setChipBackgroundColorResource(R.color.primary)
                chip.setTextColor(resources.getColor(R.color.white, null))
            }
            chip.setOnClickListener {
                chipGroup.clearCheck()
                chip.isChecked = true
                for (i in 0 until chipGroup.childCount) {
                    val c = chipGroup.getChildAt(i) as com.google.android.material.chip.Chip
                    c.setChipBackgroundColorResource(R.color.divider)
                    c.setTextColor(resources.getColor(R.color.text_secondary, null))
                }
                chip.setChipBackgroundColorResource(R.color.primary)
                chip.setTextColor(resources.getColor(R.color.white, null))
                filterApps(category)
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
                val results = appListRepository.searchApps(query)
                displayApps(results)
            } else {
                displayApps(allApps)
            }
            true
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                if (query.isEmpty()) {
                    displayApps(allApps)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = appListRepository.getInstalledApps()
            withContext(Dispatchers.Main) {
                allApps = apps
                displayApps(apps)
            }
        }
    }

    private fun filterApps(category: String) {
        if (category == "全部") {
            displayApps(allApps)
            return
        }
        val filtered = allApps.filter { app ->
            val name = app.appName.lowercase()
            when (category) {
                "学习" -> name.contains("edu") || name.contains("learn") || name.contains("study") || name.contains("class") || name.contains("homework") || name.contains("course")
                "益智" -> name.contains("game") || name.contains("puzzle") || name.contains("brain") || name.contains("math")
                "阅读" -> name.contains("book") || name.contains("read") || name.contains("novel") || name.contains("reader")
                "艺术" -> name.contains("art") || name.contains("draw") || name.contains("paint") || name.contains("music") || name.contains("photo")
                "音乐" -> name.contains("music") || name.contains("song") || name.contains("audio")
                else -> true
            }
        }
        displayApps(filtered)
    }

    private fun displayApps(apps: List<AppInfo>) {
        val container = binding.llAppList
        container.removeAllViews()

        if (apps.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = if (allApps.isEmpty()) "正在加载应用列表..." else "未找到匹配的应用"
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_hint, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (200 * resources.displayMetrics.density).toInt()
                )
            }
            container.addView(emptyView)
            return
        }

        val density = resources.displayMetrics.density
        for (app in apps) {
            val cardView = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * density).toInt() }
                radius = (16 * density).toFloat()
                cardElevation = (2 * density).toFloat()
                setCardBackgroundColor(resources.getColor(R.color.card_bg, null))
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }

            val iconView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (app.icon != null) {
                    setImageDrawable(app.icon)
                } else {
                    setImageResource(R.drawable.ic_launcher_simple)
                }
            }
            row.addView(iconView)

            val textContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = (16 * density).toInt()
                }
            }

            textContainer.addView(TextView(requireContext()).apply {
                text = app.appName
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            textContainer.addView(TextView(requireContext()).apply {
                text = app.packageName
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_hint, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (4 * density).toInt() }
            })

            row.addView(textContainer)

            cardView.addView(row)
            container.addView(cardView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

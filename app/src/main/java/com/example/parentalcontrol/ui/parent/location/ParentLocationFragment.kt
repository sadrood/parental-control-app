package com.example.parentalcontrol.ui.parent.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentLocationBinding
import com.example.parentalcontrol.ui.base.BaseFragment
import com.example.parentalcontrol.util.LogUtil
import com.example.parentalcontrol.util.SettingsManager

class ParentLocationFragment : BaseFragment<FragmentParentLocationBinding>() {

    companion object {
        private const val TAG = "ParentLocation"
    }

    private lateinit var settingsManager: SettingsManager

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentParentLocationBinding {
        return FragmentParentLocationBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            settingsManager = SettingsManager(requireContext())
            setupViews()
            setupClickListeners()
            populateLocationRecords()
        } catch (e: Exception) {
            LogUtil.e(TAG, "初始化失败", e)
        }
    }

    private fun setupViews() {
        safeRun {
            tvChildLocationName.text = "${settingsManager.childName} · 当前位置"
        }
    }

    private fun setupClickListeners() {
        safeRun {
            cardHomeZone.setOnClickListener {
                Toast.makeText(context, "家：安全区域已设置", Toast.LENGTH_SHORT).show()
            }
            cardSchoolZone.setOnClickListener {
                Toast.makeText(context, "学校：安全区域已设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateLocationRecords() {
        safeRun {
            llLocationRecords.removeAllViews()
            val records = listOf(
                Triple("北京市朝阳区望京SOHO", "2分钟前", "停留15分钟"),
                Triple("北京市海淀区中关村", "30分钟前", "停留45分钟"),
                Triple("北京市西城区金融街", "1小时前", "停留20分钟")
            )
            val density = resources.displayMetrics.density
            records.forEach { (address, time, duration) ->
                llLocationRecords.addView(createRecordView(address, time, duration, density))
            }
        }
    }

    private fun createRecordView(address: String, time: String, duration: String, density: Float): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())

            addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((8 * density).toInt(), (8 * density).toInt()).apply {
                    rightMargin = (10 * density).toInt()
                }
                setBackgroundColor(resources.getColor(R.color.text_hint, null))
            })

            addView(TextView(requireContext()).apply {
                text = address
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(requireContext()).apply {
                text = "$duration · $time"
                textSize = 11f
                setTextColor(resources.getColor(R.color.text_hint, null))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = (8 * density).toInt()
                }
            })
        }
    }
}

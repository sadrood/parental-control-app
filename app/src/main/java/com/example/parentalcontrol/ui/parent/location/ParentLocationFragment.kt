package com.example.parentalcontrol.ui.parent.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.parentalcontrol.R
import com.example.parentalcontrol.databinding.FragmentParentLocationBinding
import com.example.parentalcontrol.util.SettingsManager

class ParentLocationFragment : Fragment() {

    private var _binding: FragmentParentLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        setupViews()
        loadChildLocation()
    }

    private fun setupViews() {
        binding.tvChildLocationName.text = "${settingsManager.childName} · 当前位置"
    }

    private fun loadChildLocation() {
        // TODO: 从服务器获取儿童设备的实时位置
        // 儿童端会上报定位到服务器，家长端从此接口获取
        // GET /api/device/:childId/location
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

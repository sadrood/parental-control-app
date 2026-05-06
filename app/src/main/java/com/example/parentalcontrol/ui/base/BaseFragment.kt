package com.example.parentalcontrol.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.example.parentalcontrol.util.LogUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    companion object {
        @PublishedApi internal const val TAG = "BaseFragment"
    }

    @PublishedApi internal var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding accessed after destroy")

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    protected fun <T> LiveData<T>.observeSafe(observer: (T) -> Unit) {
        observe(viewLifecycleOwner) { value ->
            if (_binding != null) {
                try {
                    observer(value)
                } catch (e: Exception) {
                    LogUtil.w(TAG, "LiveData observer error: ${e.message}")
                }
            }
        }
    }

    protected fun <T> Flow<T>.collectSafe(collector: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect {
                    if (_binding != null) {
                        try {
                            collector(it)
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Flow collect error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    protected fun <T> Flow<T>.collectLatestSafe(collector: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectLatest {
                    if (_binding != null) {
                        try {
                            collector(it)
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Flow collect error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    protected inline fun safeRun(crossinline block: VB.() -> Unit) {
        try {
            _binding?.let { binding ->
                block(binding)
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "safeRun error: ${e.message}")
        }
    }
}

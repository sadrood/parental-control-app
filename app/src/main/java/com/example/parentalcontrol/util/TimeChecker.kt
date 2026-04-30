package com.example.parentalcontrol.util

import com.example.parentalcontrol.data.entity.TimeRule
import java.util.*

object TimeChecker {

    fun isCurrentlyAllowed(rules: List<TimeRule>): Boolean {
        val now = Calendar.getInstance()
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=周日, 2=周一...
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // 转换为 1=周一, 7=周日
        val dayOfWeek = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1

        val enabledRules = rules.filter { it.isEnabled }

        if (enabledRules.isEmpty()) return true // 没有规则时默认允许

        // 检查是否有禁止规则匹配当前时间
        for (rule in enabledRules) {
            val ruleDays = rule.weekdays.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (dayOfWeek !in ruleDays) continue

            val startMinutes = rule.startHour * 60 + rule.startMinute
            val endMinutes = rule.endHour * 60 + rule.endMinute

            if (isTimeInRange(currentMinutes, startMinutes, endMinutes)) {
                return rule.isAllowed
            }
        }

        return true // 不在任何规则时段内，默认允许
    }

    private fun isTimeInRange(current: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            current in start until end
        } else {
            // 跨午夜的情况
            current >= start || current < end
        }
    }

    fun getRemainingTimeToday(dailyLimitMinutes: Int, usedMinutes: Long): Int {
        val remaining = dailyLimitMinutes - (usedMinutes / 60000)
        return if (remaining > 0) remaining.toInt() else 0
    }
}

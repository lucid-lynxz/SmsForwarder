package cn.ppps.forwarder.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.ppps.forwarder.R
import com.xuexiang.xutil.resource.ResUtils.getString
import kotlinx.parcelize.Parcelize
import java.util.Calendar
import java.util.Date

/**
 * 允许转发时间窗口：一种可被转发规则引用的过滤规则，命中窗口才放行，未命中则跳过整条规则
 */
@Parcelize
@Entity(tableName = "TimeWindow")
data class TimeWindow(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "name", defaultValue = "") var name: String = "",
    @ColumnInfo(name = "day_of_week", defaultValue = "") var dayOfWeek: String = "",
    @ColumnInfo(name = "period_start", defaultValue = "") var periodStart: String = "",
    @ColumnInfo(name = "period_end", defaultValue = "") var periodEnd: String = "",
    @ColumnInfo(name = "time") var time: Date = Date(),
) : Parcelable {

    fun isInWindow(calendar: Calendar = Calendar.getInstance()): Boolean {
        return isDayMatched(calendar) && isPeriodMatched(calendar)
    }

    private fun isDayMatched(calendar: Calendar): Boolean {
        val days = dayOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        //未勾选星期视为「每天」
        return days.isEmpty() || days.contains(calendar.get(Calendar.DAY_OF_WEEK))
    }

    private fun isPeriodMatched(calendar: Calendar): Boolean {
        val start = periodStart.toMinutes()
        val end = periodEnd.toMinutes()
        if (start == null || end == null || isAllDay(start, end)) return true
        val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        //起点含、终点不含，便于配置 08:30-12:00 与 12:00-18:00 两个相邻窗口
        return if (end < start) now >= start || now < end else now in start until end
    }

    //未配置时段、起止相同、或配置成 00:00-24:00 均视为「全天」
    private fun isAllDay(start: Int, end: Int): Boolean {
        return start == end || (start == 0 && end == DAY_END_MINUTE)
    }

    private fun String.toMinutes(): Int? {
        val parts = split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    fun buildDescription(): String {
        val dayMap = mapOf(
            Calendar.SUNDAY to R.string.sun,
            Calendar.MONDAY to R.string.mon,
            Calendar.TUESDAY to R.string.tue,
            Calendar.WEDNESDAY to R.string.wed,
            Calendar.THURSDAY to R.string.thu,
            Calendar.FRIDAY to R.string.fri,
            Calendar.SATURDAY to R.string.sat,
        )
        val days = dayOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            .sortedBy { it }
            .mapNotNull { dayMap[it] }
            .joinToString("/") { getString(it) }
        val period = getString(R.string.time_window_all_day)
        return (days.ifEmpty { getString(R.string.time_window_all_days) }) + " " +
            if (isAllDay(periodStart.toMinutes() ?: 0, periodEnd.toMinutes() ?: 0)) period else "$periodStart~$periodEnd"
    }

    companion object {
        private const val DAY_END_MINUTE = 24 * 60
    }

}

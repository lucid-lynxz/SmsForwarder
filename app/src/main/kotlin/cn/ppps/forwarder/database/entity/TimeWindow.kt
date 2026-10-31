package cn.ppps.forwarder.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.ppps.forwarder.R
import com.xuexiang.xutil.resource.ResUtils.getString
import kotlinx.parcelize.Parcelize
import java.util.BitSet
import java.util.Calendar
import java.util.Date

/**
 * 允许转发时间窗口内的一个时间段，形如 09:30-11:50；起止相同或 00:00-24:00 视为全天，结束早于开始视为跨天
 */
data class TimePeriod(val start: String = "", val end: String = "") {

    //起点含、终点不含，便于配置 09:30-11:50 与 11:50-13:30 两个相邻时间段
    fun covers(minute: Int): Boolean = toBitSet().get(minute)

    fun toBitSet(): BitSet {
        val covered = BitSet(MINUTES_OF_DAY)
        val from = start.toMinutes()
        val to = end.toMinutes()
        when {
            from == null || to == null || from == to || (from == 0 && to == MINUTES_OF_DAY) -> covered.set(0, MINUTES_OF_DAY)
            to < from -> {
                covered.set(from, MINUTES_OF_DAY)
                covered.set(0, to)
            }

            else -> covered.set(from, to)
        }
        return covered
    }

    override fun toString(): String = "$start~$end"

    companion object {
        private const val MINUTES_OF_DAY = 24 * 60

        fun toMinutes(time: String): Int? {
            val parts = time.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].trim().toIntOrNull() ?: return null
            val minute = parts[1].trim().toIntOrNull() ?: return null
            val total = hour * 60 + minute
            return if (total in 0..MINUTES_OF_DAY) total else null
        }
    }

}

private fun String.toMinutes(): Int? = TimePeriod.toMinutes(this)

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
    //兼容旧版本：镜像第一个时间段的起止。minSdk 19 不能 DROP COLUMN，Room 校验也要求这两列在实体里存在
    @ColumnInfo(name = "period_start", defaultValue = "") var periodStart: String = "",
    @ColumnInfo(name = "period_end", defaultValue = "") var periodEnd: String = "",
    //多个时间段，以分号分隔（09:30-11:50;13:30-18:30），为空时回落到 period_start/period_end
    @ColumnInfo(name = "periods", defaultValue = "") var periods: String = "",
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
        val list = periodList
        //未配置时段视为「全天」
        if (list.isEmpty()) return true
        val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return list.any { it.covers(now) }
    }

    val periodList: List<TimePeriod>
        get() = periods.split(";").mapNotNull { it.toTimePeriod() }
            //旧数据与旧备份只有 period_start/period_end 一对起止
            .ifEmpty { listOf(TimePeriod(periodStart, periodEnd)) }

    fun isAllDay(): Boolean {
        val covered = BitSet(MINUTES_OF_DAY)
        periodList.forEach { covered.or(it.toBitSet()) }
        return covered.cardinality() >= MINUTES_OF_DAY
    }

    private fun String.toTimePeriod(): TimePeriod? {
        val parts = split("-")
        if (parts.size != 2) return null
        return TimePeriod(parts[0].trim(), parts[1].trim())
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
        val periods = if (isAllDay()) getString(R.string.time_window_all_day) else periodList.joinToString(", ") { it.toString() }
        return (days.ifEmpty { getString(R.string.time_window_all_days) }) + " " + periods
    }

    companion object {
        private const val MINUTES_OF_DAY = 24 * 60

        fun periodsToString(periods: List<TimePeriod>): String = periods.joinToString(";") { "${it.start}-${it.end}" }

        //相邻端点不算重叠；全天段与任何其他段都算重叠
        fun hasOverlappedPeriods(periods: List<TimePeriod>): Boolean {
            val covered = BitSet(MINUTES_OF_DAY)
            for (period in periods) {
                val current = period.toBitSet()
                current.and(covered)
                if (current.cardinality() > 0) return true
                covered.or(period.toBitSet())
            }
            return false
        }
    }

}

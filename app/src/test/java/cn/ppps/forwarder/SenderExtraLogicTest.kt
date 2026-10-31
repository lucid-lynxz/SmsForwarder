package cn.ppps.forwarder

import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.database.entity.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 验证「允许转发时间窗口」与「内容打码规则」的核心判定逻辑（纯函数，无需 Android 运行时）。
 */
class SenderExtraLogicTest {

    private fun cal(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

    // ---------- 时间窗口 ----------

    @Test
    fun allDay_whenNoPeriod() {
        val w = TimeWindow(name = "全天", dayOfWeek = "", periodStart = "", periodEnd = "")
        assertTrue(w.isInWindow(cal(Calendar.WEDNESDAY, 3, 5)))
    }

    @Test
    fun allDay_whenStartEqualsEnd() {
        val w = TimeWindow(name = "全天", dayOfWeek = "", periodStart = "08:00", periodEnd = "08:00")
        assertTrue(w.isInWindow(cal(Calendar.WEDNESDAY, 23, 0)))
    }

    @Test
    fun allDay_when0000to2400() {
        val w = TimeWindow(name = "全天", dayOfWeek = "", periodStart = "00:00", periodEnd = "24:00")
        assertTrue(w.isInWindow(cal(Calendar.MONDAY, 13, 37)))
    }

    @Test
    fun normalPeriod_boundaries() {
        val w = TimeWindow(name = "工作", dayOfWeek = "", periodStart = "09:00", periodEnd = "18:00")
        assertTrue("起点含", w.isInWindow(cal(Calendar.TUESDAY, 9, 0)))
        assertTrue("区间内", w.isInWindow(cal(Calendar.TUESDAY, 10, 0)))
        assertFalse("终点不含", w.isInWindow(cal(Calendar.TUESDAY, 18, 0)))
        assertFalse("区间前", w.isInWindow(cal(Calendar.TUESDAY, 8, 59)))
        assertFalse("区间后(23:15)", w.isInWindow(cal(Calendar.TUESDAY, 23, 15)))
    }

    @Test
    fun crossMidnightPeriod() {
        val w = TimeWindow(name = "夜间", dayOfWeek = "", periodStart = "22:00", periodEnd = "06:00")
        assertTrue("23:15 在窗口", w.isInWindow(cal(Calendar.FRIDAY, 23, 15)))
        assertTrue("03:00 在窗口", w.isInWindow(cal(Calendar.SATURDAY, 3, 0)))
        assertFalse("12:00 不在", w.isInWindow(cal(Calendar.SATURDAY, 12, 0)))
    }

    @Test
    fun weekdayFilter() {
        // 仅周三(4)、周五(6)
        val w = TimeWindow(name = "周中", dayOfWeek = "4,6", periodStart = "", periodEnd = "")
        assertTrue(w.isInWindow(cal(Calendar.WEDNESDAY, 12, 0)))
        assertTrue(w.isInWindow(cal(Calendar.FRIDAY, 12, 0)))
        assertFalse(w.isInWindow(cal(Calendar.THURSDAY, 12, 0)))
    }

    @Test
    fun weekdayAndPeriod_areAndRelation() {
        val w = TimeWindow(name = "组合", dayOfWeek = "2", periodStart = "09:00", periodEnd = "18:00")
        assertTrue(w.isInWindow(cal(Calendar.MONDAY, 10, 0)))    // 周一 + 时段内
        assertFalse(w.isInWindow(cal(Calendar.MONDAY, 20, 0)))   // 周一 + 时段外
        assertFalse(w.isInWindow(cal(Calendar.TUESDAY, 10, 0)))  // 周二 + 时段内
    }

    // ---------- 内容打码 ----------

    @Test
    fun mask_replacesMatch() {
        val r = MaskRule(name = "遮六位", regex = "[0-9]{6}", replacement = "******")
        assertEquals("您的验证码是 ******，请勿泄露", r.mask("您的验证码是 123456，请勿泄露"))
    }

    @Test
    fun mask_emptyReplacementDeletes() {
        val r = MaskRule(name = "删词", regex = "请勿泄露", replacement = "")
        assertEquals("您的验证码是 123456，", r.mask("您的验证码是 123456，请勿泄露"))
    }

    @Test
    fun mask_chainedInSortOrder() {
        // 复现 applyMaskRules：按 sort 升序依次替换
        val rules = listOf(
            MaskRule(name = "遮六位", regex = "[0-9]{6}", replacement = "******", sort = 1),
            MaskRule(name = "删词", regex = "请勿泄露", replacement = "", sort = 2),
        )
        var content = "您的验证码是 123456，请勿泄露"
        for (r in rules.sortedBy { it.sort }) content = r.mask(content)
        assertEquals("您的验证码是 ******，", content)
    }

    @Test
    fun mask_noMatchKeepsOriginal() {
        val r = MaskRule(name = "遮六位", regex = "[0-9]{6}", replacement = "******")
        assertEquals("没有数字内容", r.mask("没有数字内容"))
    }

    @Test
    fun mask_supportsCaptureGroup() {
        val r = MaskRule(name = "保留后四位", regex = "(\\d{3})\\d{3}", replacement = "$1***")
        assertEquals("卡号 123*** 结尾", r.mask("卡号 123456 结尾"))
    }
}

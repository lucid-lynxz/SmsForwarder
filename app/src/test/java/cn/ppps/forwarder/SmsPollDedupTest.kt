package cn.ppps.forwarder

import cn.ppps.forwarder.utils.SmsPollUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证短信轮询兜底与广播转发共用的去重逻辑（纯函数部分，不依赖 Android/SP）。
 */
class SmsPollDedupTest {

    @Test
    fun keyCombinesAddressAndBody() {
        assertEquals("10086|您的验证码是123456", SmsPollUtils.keyOf("10086", "您的验证码是123456"))
        //同内容不同号码视为不同短信
        assertTrue(SmsPollUtils.keyOf("10086", "abc") != SmsPollUtils.keyOf("10010", "abc"))
    }

    @Test
    fun pruneDropsEntriesOlderThanTtl() {
        val now = 1_000_000L
        val list = mutableListOf(
            SmsPollUtils.SeenSms(now - 100_000L, "stale"),
            SmsPollUtils.SeenSms(now - 1_000L, "fresh")
        )
        SmsPollUtils.prune(list, now)
        assertEquals(1, list.size)
        assertEquals("fresh", list[0].key)
    }

    @Test
    fun pruneCapsAtMaxKeepingNewest() {
        val now = 1_000_000L
        val list = (0 until 100).map { SmsPollUtils.SeenSms(now - it.toLong(), "k$it") }.toMutableList()
        SmsPollUtils.prune(list, now)
        assertEquals(64, list.size)
        //takeLast 保留最新加入的尾部 64 条
        assertEquals("k99", list.last().key)
        assertEquals("k36", list.first().key)
    }
}

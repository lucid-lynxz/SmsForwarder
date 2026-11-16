package cn.ppps.forwarder

import cn.ppps.forwarder.utils.SmsCodeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证「疑似验证码短信」的关键词识别，短信读取测试页与 Bark 自动复制共用这条规则。
 */
class SmsCodeLogicTest {

    @Test
    fun commonCodeSmsAreRecognized() {
        assertTrue(SmsCodeUtils.looksLikeCode("【某某支付】您的验证码是123456，5分钟内有效。"))
        assertTrue(SmsCodeUtils.looksLikeCode("您收到一条动态密码：12 3456"))
        assertTrue(SmsCodeUtils.looksLikeCode("Your verification code is 1234"))
        assertTrue(SmsCodeUtils.looksLikeCode("CODE: ABC123，请勿泄露给他人。"))
        assertTrue(SmsCodeUtils.looksLikeCode("您的動態碼為 8888"))
        assertTrue(SmsCodeUtils.looksLikeCode("本次交易的交易码为1234"))
    }

    @Test
    fun marketingSmsAreNotRecognized() {
        assertFalse(SmsCodeUtils.looksLikeCode("双十一全场五折，点击链接抢购。"))
        assertFalse(SmsCodeUtils.looksLikeCode("您的包裹已到达驿站，取件地址：某某路1号"))
    }

    @Test
    fun replyCodeIsExcluded() {
        // 「回复验证码」属于引导用户回复，不是短信里带的码
        assertFalse(SmsCodeUtils.looksLikeCode("请回复验证码完成绑定"))
        assertTrue(SmsCodeUtils.looksLikeCode("验证码123456，请勿回复"))
    }
}

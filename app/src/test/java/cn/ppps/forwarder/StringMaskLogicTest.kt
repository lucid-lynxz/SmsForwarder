package cn.ppps.forwarder

import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.utils.PinyinUtils
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证「姓名马赛克」的拼音首字母转换与字符串类型打码规则的字面替换逻辑（纯函数，无需 Android 运行时）。
 */
class StringMaskLogicTest {

    // ---------- 拼音首字母 ----------

    @Test fun initials_twoChars() {
        assertEquals("ZS", PinyinUtils.initials("张三"))
    }

    @Test fun initials_threeChars() {
        assertEquals("ZSF", PinyinUtils.initials("张三丰"))
    }

    @Test fun initials_surnamePolyphone() {
        //查作姓氏读 Zha，日常读 Cha，首字要按姓氏取
        assertEquals("ZXS", PinyinUtils.initials("查先生"))
    }

    @Test fun initials_keepsLatinAndDigits() {
        assertEquals("TOM", PinyinUtils.initials("Tom"))
        assertEquals("WX2", PinyinUtils.initials("王小2"))
    }

    @Test fun initials_trimsAndBlanks() {
        assertEquals("ZS", PinyinUtils.initials("  张三  "))
        assertEquals("", PinyinUtils.initials("   "))
    }

    // ---------- 字符串类型规则 ----------

    @Test fun stringType_masksLiteral() {
        val rule = MaskRule(name = "张三", regex = "张三", replacement = "ZS", type = MaskRule.MASK_TYPE_STRING)
        assertEquals("联系人ZS已签收", rule.mask("联系人张三已签收"))
    }

    @Test fun stringType_replacesEveryOccurrence() {
        val rule = MaskRule(name = "张三", regex = "张三", replacement = "ZS", type = MaskRule.MASK_TYPE_STRING)
        assertEquals("ZS和ZS", rule.mask("张三和张三"))
    }

    @Test fun stringType_doesNotInterpretRegexMetaChars() {
        val rule = MaskRule(name = "括号", regex = "(张三)", replacement = "ZS", type = MaskRule.MASK_TYPE_STRING)
        assertEquals("收到 ZS", rule.mask("收到 (张三)"))
    }

    @Test fun stringType_emptyReplacementDeletes() {
        val rule = MaskRule(name = "张三", regex = "张三", replacement = "", type = MaskRule.MASK_TYPE_STRING)
        assertEquals("和", rule.mask("张三和张三"))
    }

    @Test fun regexType_stillWorks() {
        val rule = MaskRule(name = "遮六位", regex = "[0-9]{6}", replacement = "******")
        assertEquals("验证码 ******", rule.mask("验证码 123456"))
    }

    @Test fun legacyJsonWithoutTypeStaysRegex() {
        //旧版本导出的配置里没有 type 字段，反序列化后必须落回正则类型，否则老规则会被当成字面匹配
        val rule = Gson().fromJson(
            """{"name":"遮六位","regex":"[0-9]{6}","replacement":"******"}""",
            MaskRule::class.java
        )
        assertEquals(MaskRule.MASK_TYPE_REGEX, rule.type)
        assertEquals("验证码 ******", rule.mask("验证码 123456"))
    }

}

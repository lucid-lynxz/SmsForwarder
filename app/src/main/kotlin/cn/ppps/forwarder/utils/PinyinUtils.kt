package cn.ppps.forwarder.utils

import com.github.promeg.pinyinhelper.Pinyin

object PinyinUtils {

    //姓氏读法与常用字读法首字母不同的字，仅作用于姓名首字（如 查 as 姓读 Zha，日常读 Cha）
    private val SURNAME_INITIALS = mapOf(
        '查' to 'Z', '单' to 'S', '解' to 'X', '曾' to 'Z', '区' to 'O',
        '招' to 'Q', '乐' to 'Y', '覃' to 'Q', '折' to 'S', '仇' to 'Q', '召' to 'S',
    )

    /**
     * 取每个汉字的拼音首字母并大写，非汉字原样保留，例如 张三 -> ZS、李四丰 -> LSF
     */
    fun initials(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val builder = StringBuilder(trimmed.length)
        for ((index, ch) in trimmed.withIndex()) {
            val surnameInitial = if (index == 0) SURNAME_INITIALS[ch] else null
            when {
                surnameInitial != null -> builder.append(surnameInitial)
                Pinyin.isChinese(ch) -> builder.append(Pinyin.toPinyin(ch).trim().first())
                else -> builder.append(ch)
            }
        }
        return builder.toString().uppercase()
    }

}

package cn.ppps.forwarder.utils

/**
 * 同一条消息被重复上报时的内容比对工具
 *
 * APP 通知（尤其微信）对同一条消息常常先 post 再 update：一次给的是摘要（可能带省略号），
 * 一次给的是全文，update 时还可能在前面补上群名。整串精确比对识别不出是同一条消息，
 * 于是同一个通道会收到两条。
 */
object DuplicateMsgUtils {

    /** 短于这个长度不做包含判定，避免「收到」/「收到，好的」这类不同消息被误判为重复 */
    private const val MIN_CONTAIN_LENGTH = 10

    private val whitespaceRegex = Regex("\\s+")
    private val trailingEllipsisRegex = Regex("(?:\\.{3}|\\u2026|\\u22ef|。{3})$")

    private fun normalize(content: String?): String =
        trailingEllipsisRegex.replace(whitespaceRegex.replace(content ?: "", ""), "")

    /** 内容相同，或者一方包含另一方（摘要与全文、全文前面多出了群名） */
    fun isSameMessageContent(a: String?, b: String?): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if (x.length < MIN_CONTAIN_LENGTH && y.length < MIN_CONTAIN_LENGTH) return false
        return x.contains(y) || y.contains(x)
    }
}

package cn.ppps.forwarder.utils

/**
 * 验证码类短信的关键词识别。Bark 通道的「自动复制验证码」与短信读取测试页共用这一条规则。
 */
object SmsCodeUtils {

    val CODE_KEYWORD: Regex = Regex(
        "(?<!回复)(验证码|授权码|校验码|检验码|确认码|激活码|动态码|安全码|(验证)?代码|校验代码|检验代码|激活代码|确认代码|动态代码|安全代码|登入码|认证码|识别码|短信口令|动态密码|交易码|上网密码|动态口令|随机码|驗證碼|授權碼|校驗碼|檢驗碼|確認碼|激活碼|動態碼|(驗證)?代碼|校驗代碼|檢驗代碼|確認代碼|激活代碼|動態代碼|登入碼|認證碼|識別碼|一次性密码|[Cc][Oo][Dd][Ee]|[Vv]erification)"
    )

    fun looksLikeCode(content: String): Boolean = CODE_KEYWORD.containsMatchIn(content)
}

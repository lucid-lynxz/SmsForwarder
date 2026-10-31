package cn.ppps.forwarder.server.component

import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.HttpServerUtils
import com.xuexiang.xutil.resource.ResUtils.getString
import com.yanzhenjie.andserver.annotation.Interceptor
import com.yanzhenjie.andserver.error.HttpException
import com.yanzhenjie.andserver.framework.HandlerInterceptor
import com.yanzhenjie.andserver.framework.handler.MethodHandler
import com.yanzhenjie.andserver.framework.handler.RequestHandler
import com.yanzhenjie.andserver.http.HttpMethod
import com.yanzhenjie.andserver.http.HttpRequest
import com.yanzhenjie.andserver.http.HttpResponse

@Suppress("PrivatePropertyName")
@Interceptor
class LoggerInterceptor : HandlerInterceptor {

    private val TAG: String = "LoggerInterceptor"

    override fun onIntercept(
        request: HttpRequest,
        respons: HttpResponse,
        handler: RequestHandler,
    ): Boolean {
        if (handler is MethodHandler) {
            val httpPath = request.path
            val method: HttpMethod = request.method
            val valueMap = request.parameter
            Log.i(TAG, "Path: $httpPath")
            Log.i(TAG, "Method: " + method.value())
            Log.i(TAG, "Param: $valueMap")

            //跨域来源防护：浏览器脚本只能在同源页面里调用本机接口，其他 Origin 一律拒绝。
            //原生 HTTP 客户端（curl / 其它 App）不发 Origin，因此不受影响。
            val origin = request.getHeader("Origin")
            if (!origin.isNullOrEmpty() && !isSameOrigin(origin, request.getHeader("Host"))) {
                Log.e(TAG, "reject cross-origin request from = $origin to $httpPath")
                throw HttpException(403, getString(R.string.cors_origin_denied))
            }

            //判断是否开启该功能
            if (
                (httpPath.startsWith("/clone") && !HttpServerUtils.enableApiClone)
                || (httpPath.startsWith("/sms/query") && !HttpServerUtils.enableApiSmsQuery)
                || (httpPath.startsWith("/sms/send") && !HttpServerUtils.enableApiSmsSend)
                || (httpPath.startsWith("/call/query") && !HttpServerUtils.enableApiCallQuery)
                || (httpPath.startsWith("/contact/query") && !HttpServerUtils.enableApiContactQuery)
                || (httpPath.startsWith("/contact/add") && !HttpServerUtils.enableApiContactAdd)
                || (httpPath.startsWith("/wol/send") && !HttpServerUtils.enableApiWol)
                || (httpPath.startsWith("/location/query") && !HttpServerUtils.enableApiLocation)
                || (httpPath.startsWith("/battery/query") && !HttpServerUtils.enableApiBatteryQuery)
            ) {
                throw HttpException(500, getString(R.string.disabled_on_the_server))
            }

            /*
            //注意：这里读取body会导致 MessageConverter 报错：RequestBody is missing.
            val body = request.body?.string()
            Log.i(TAG, "Body: $body")
            */
        }
        return false
    }

    //Origin 形如 http://host:port，与请求的 Host 头一致即视为同源
    private fun isSameOrigin(origin: String, host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val separator = origin.indexOf("://")
        if (separator < 0) return false
        val scheme = origin.substring(0, separator)
        if (!scheme.equals("http", true) && !scheme.equals("https", true)) return false
        return origin.substring(separator + 3).equals(host, ignoreCase = true)
    }
}

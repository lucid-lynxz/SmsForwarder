package cn.ppps.forwarder.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import cn.ppps.forwarder.utils.HTTP_SERVER_TIME_OUT
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import com.yanzhenjie.andserver.AndServer
import com.yanzhenjie.andserver.Server
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@Suppress("PrivatePropertyName")
class HttpServerService : Service(), Server.ServerListener {

    private val TAG: String = HttpServerService::class.java.simpleName
    private var started = false
    private val server by lazy {
        //默认只监听回环地址，避免局域网内其它设备直连本机的远程控制接口
        val listenAddress = if (HttpServerUtils.enableServerLanAccess) "0.0.0.0" else "127.0.0.1"
        Log.i(TAG, "listen address: $listenAddress")
        AndServer.webServer(this)
            .inetAddress(InetAddress.getByName(listenAddress))
            .port(HttpServerUtils.serverPort)
            .listener(this)
            .timeout(HTTP_SERVER_TIME_OUT, TimeUnit.SECONDS)
            .build()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        //纯客户端模式
        if (SettingUtils.enablePureClientMode) return

        if (!HttpServerUtils.securityReady()) {
            Log.e(TAG, "onCreate: 未配置安全措施或对应密钥，拒绝启动HttpServer")
            stopSelf()
            return
        }

        Log.i(TAG, "onCreate: ")
        started = true
        server.startup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ")
        if (!started && HttpServerUtils.securityReady()) {
            started = true
            server.startup()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        //纯客户端模式
        if (SettingUtils.enablePureClientMode) return

        Log.i(TAG, "onDestroy: ")
        if (started) {
            started = false
            server.shutdown()
        }
    }

    override fun onException(e: Exception?) {
        Log.i(TAG, "onException: ")
    }

    override fun onStarted() {
        Log.i(TAG, "onStarted: ")
    }

    override fun onStopped() {
        Log.i(TAG, "onStopped: ")
    }
}
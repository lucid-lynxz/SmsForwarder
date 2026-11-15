package cn.ppps.forwarder.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import cn.ppps.forwarder.App
import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.utils.ACTION_RESTART
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.ACTION_STOP
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.LocationUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.TASK_CONDITION_LEAVE_ADDRESS
import cn.ppps.forwarder.utils.TASK_CONDITION_TO_ADDRESS
import cn.ppps.forwarder.utils.TaskWorker
import cn.ppps.forwarder.utils.task.TaskUtils
import cn.ppps.forwarder.workers.LocationWorker
import com.king.location.LocationErrorCode
import com.king.location.OnExceptionListener
import com.king.location.OnLocationListener
import com.xuexiang.xaop.util.PermissionUtils
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("SimpleDateFormat")
@Suppress("PrivatePropertyName", "DEPRECATION")
class LocationService : Service() {

    private val TAG: String = LocationService::class.java.simpleName
    private val locationStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                handleLocationStatusChanged()
            }
        }
    }

    companion object {
        var isRunning = false

        private val onceLock = Any()
        private var pendingLatch: CountDownLatch? = null
        private var locationHandler: Handler? = null

        //king-location 的 startLocation 内部要建 Handler，调用线程必须有 Looper；
        //转发/测试跑在无 Looper 的 worker 线程上，故把定位动作派发到专用 HandlerThread。
        private fun locationHandler(): Handler {
            synchronized(onceLock) {
                locationHandler?.let { return it }
                val thread = HandlerThread("location-once")
                thread.start()
                return Handler(thread.looper).also { locationHandler = it }
            }
        }

        //按需单次定位：发起一次定位并等待结果（超时返回 false，调用方回退缓存）。
        //只在转发真正需要位置时调用，避免常驻定位监听让系统隐私指示常亮。
        fun requestLocationOnce(timeoutMs: Long): Boolean {
            if (!SettingUtils.enableLocation) return false
            if (!PermissionUtils.isGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)) return false
            if (!LocationUtils.isLocationEnabled(App.context) || !LocationUtils.hasLocationCapability(App.context)) return false

            val latch = CountDownLatch(1)
            synchronized(onceLock) {
                val existing = pendingLatch
                if (existing != null) {
                    //已有一次定位在跑，直接等它的结果，避免重复发起
                    return existing.await(timeoutMs, TimeUnit.MILLISECONDS)
                }
                pendingLatch = latch
            }

            return try {
                locationHandler().post {
                    try {
                        val locationOption = App.LocationClient.getLocationOption()
                            .setAccuracy(SettingUtils.locationAccuracy)
                            .setPowerRequirement(SettingUtils.locationPowerRequirement)
                            .setOnceLocation(true)
                            .setLastKnownLocation(false)
                        App.LocationClient.setLocationOption(locationOption)
                        if (App.LocationClient.isStarted()) App.LocationClient.stopLocation()
                        App.LocationClient.startLocation()
                    } catch (e: Exception) {
                        Log.e("LocationService", "requestLocationOnce start: ${e.message}")
                        signalOnce()
                    }
                }
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Log.e("LocationService", "requestLocationOnce: ${e.message}")
                false
            } finally {
                synchronized(onceLock) { if (pendingLatch === latch) pendingLatch = null }
                locationHandler?.post {
                    try {
                        if (App.LocationClient.isStarted()) App.LocationClient.stopLocation()
                    } catch (e: Exception) {
                        Log.e("LocationService", "requestLocationOnce stop: ${e.message}")
                    }
                }
            }
        }

        private fun signalOnce() {
            synchronized(onceLock) { pendingLatch?.countDown() }
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate: ")
        super.onCreate()

        if (!SettingUtils.enableLocation) return

        //注册广播接收器
        registerReceiver(locationStatusReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        startService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent == null) return START_NOT_STICKY
        Log.i(TAG, "onStartCommand: ${intent.action}")

        when {
            intent.action == ACTION_START && !isRunning -> startService()
            intent.action == ACTION_STOP && isRunning -> stopService()
            intent.action == ACTION_RESTART -> prepareLocation()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: ")
        super.onDestroy()

        if (!SettingUtils.enableLocation) return
        stopService()
        //在 Service 销毁时记得注销广播接收器
        unregisterReceiver(locationStatusReceiver)
    }

    private fun startService() {
        try {
            if (SettingUtils.enableLocation && PermissionUtils.isGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)) {

                //设置定位监听
                App.LocationClient.setOnLocationListener(object : OnLocationListener() {
                    override fun onLocationChanged(location: Location) {
                        //位置信息
                        Log.d(TAG, "onLocationChanged(location = ${location})")

                        val locationInfoNew = LocationInfo(
                            location.longitude, location.latitude, "", App.DateFormat.format(Date(location.time)), location.provider.toString()
                        )

                        //根据坐标经纬度获取位置地址信息（WGS-84坐标系）
                        val list = App.Geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (list?.isNotEmpty() == true) {
                            locationInfoNew.address = list[0].getAddressLine(0)
                        }

                        Log.d(TAG, "locationInfoNew = $locationInfoNew")
                        HttpServerUtils.apiLocationCache = locationInfoNew
                        TaskUtils.locationInfoNew = locationInfoNew

                        //触发自动任务
                        val locationInfoOld = TaskUtils.locationInfoOld
                        if (locationInfoOld.longitude != locationInfoNew.longitude || locationInfoOld.latitude != locationInfoNew.latitude || locationInfoOld.address != locationInfoNew.address) {
                            Log.d(TAG, "locationInfoOld = $locationInfoOld")

                            val gson = Gson()
                            val locationJsonOld = gson.toJson(locationInfoOld)
                            val locationJsonNew = gson.toJson(locationInfoNew)
                            enqueueLocationWorkerRequest(TASK_CONDITION_TO_ADDRESS, locationJsonOld, locationJsonNew)
                            enqueueLocationWorkerRequest(TASK_CONDITION_LEAVE_ADDRESS, locationJsonOld, locationJsonNew)

                            TaskUtils.locationInfoOld = locationInfoNew
                        }

                        //唤醒正在等待本次单次定位的调用方
                        signalOnce()
                    }
                })

                //设置异常监听
                App.LocationClient.setOnExceptionListener(object : OnExceptionListener {
                    override fun onException(@LocationErrorCode errorCode: Int, e: Exception) {
                        //定位出现异常：唤醒等待方让其回退缓存，不再自动重启持续定位
                        Log.w(TAG, "onException(errorCode = ${errorCode}, e = ${e})")
                        signalOnce()
                    }
                })

                prepareLocation()
                isRunning = true
            } else if (!SettingUtils.enableLocation && App.LocationClient.isStarted()) {
                Log.d(TAG, "stopLocation")
                App.LocationClient.stopLocation()
                isRunning = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "startService: ${e.message}")
            isRunning = false
        }
    }

    private fun stopService() {
        //清空缓存
        HttpServerUtils.apiLocationCache = LocationInfo()
        TaskUtils.locationInfoOld = LocationInfo()

        isRunning = try {
            //如果已经开始定位，则先停止定位
            if (SettingUtils.enableLocation && App.LocationClient.isStarted()) {
                App.LocationClient.stopLocation()
            }
            stopForeground(true)
            stopSelf()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "stopService: ${e.message}")
            true
        }
    }

    //按需模式下服务只持有监听与生命周期，不主动发起定位；这里仅确保没有残留的持续定位
    private fun prepareLocation() {
        if (App.LocationClient.isStarted()) {
            App.LocationClient.stopLocation()
        }
    }

    private fun enqueueLocationWorkerRequest(
        conditionType: Int, locationJsonOld: String, locationJsonNew: String
    ) {
        val locationWorkerRequest = OneTimeWorkRequestBuilder<LocationWorker>().setInputData(
            workDataOf(
                TaskWorker.CONDITION_TYPE to conditionType, "locationJsonOld" to locationJsonOld, "locationJsonNew" to locationJsonNew
            )
        ).build()

        WorkManager.getInstance(applicationContext).enqueue(locationWorkerRequest)
    }

    private fun handleLocationStatusChanged() {
        //系统定位开关被关掉时停掉可能正在进行的单次定位；开启时不主动定位（按需触发）
        if (!LocationUtils.isLocationEnabled(App.context) || !LocationUtils.hasLocationCapability(App.context)) {
            Log.d(TAG, "handleLocationStatusChanged: 已停用")
            if (App.LocationClient.isStarted()) {
                App.LocationClient.stopLocation()
            }
            signalOnce()
        }
    }

}
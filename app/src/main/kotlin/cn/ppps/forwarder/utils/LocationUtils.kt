package cn.ppps.forwarder.utils

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import cn.ppps.forwarder.R
import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.service.LocationService
import cn.ppps.forwarder.utils.HttpServerUtils.Companion.apiLocationCache
import com.xuexiang.xutil.resource.ResUtils.getString
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

//位置块里地图链接的呈现形态：markdown/HTML 通道把链接藏进经纬度，其余通道只能保留裸链接
enum class LocationLinkStyle { PLAIN, MARKDOWN, HTML }

@Suppress("DEPRECATION")
object LocationUtils {

    private const val LOCATION_MODE_OFF = 0

    //钉钉 markdown 会把连续换行折叠，用私有区占位符「先标记后解析」区分紧挨换行与可见间隔
    const val MD_LF = "@@LF@@"   //单个换行（位置块内部/模板单换行，解析为普通分行）
    const val MD_GAP = "@@GAP@@"  //空行/位置块前，解析为 &nbsp; 占位形成的可见间隔

    //WGS-84 转 GCJ-02（国测局坐标）所需参数，高德/腾讯地图使用 GCJ-02，直接给 GPS 原始坐标会偏移几十到几百米
    private const val SEMI_MAJOR_AXIS = 6378245.0
    private const val ECCENTRICITY_SQUARED = 0.00669342162296594323
    private const val PI = 3.14159265358979324
    private const val EARTH_RADIUS_M = 6371000.0

    //按需单次定位的等待上限；超时回退已有缓存，不阻塞转发太久
    private const val ON_DEMAND_TIMEOUT_MS = 8000L

    //缓存在该时长内视为新鲜，直接复用，避免同一条消息多通道/重试重复发起定位
    private const val CACHE_FRESH_MS = 60000L

    private fun hasLocationPermission(context: Context): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("LocationUtils", "hasLocationPermission: $hasPermission")
        return hasPermission
    }

    fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
            Log.d("LocationUtils", "isLocationEnabled: ${locationManager?.isLocationEnabled}")
            locationManager?.isLocationEnabled == true
        } else {
            try {
                val locationMode = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE
                )
                Log.d("LocationUtils", "isLocationEnabled: locationMode=$locationMode")
                locationMode != LOCATION_MODE_OFF
            } catch (e: Settings.SettingNotFoundException) {
                false
            }
        }
    }

    fun hasLocationCapability(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

        // 检查是否有位置权限
        if (!hasLocationPermission(context)) {
            Log.e("LocationUtils", "hasLocationCapability: no location permission")
            return false
        }

        // 检查是否有定位能力
        val hasGpsProvider = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val hasNetworkProvider = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        val hasPassiveProvider = locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true

        Log.d("LocationUtils", "hasLocationCapability: hasGpsProvider=$hasGpsProvider, hasNetworkProvider=$hasNetworkProvider, hasPassiveProvider=$hasPassiveProvider")
        return hasGpsProvider || hasNetworkProvider || hasPassiveProvider
    }

    //来源应用是否被勾选为附带位置（存储一行一个包名，空行与首尾空格自动忽略）
    fun appendLocationEnabledFor(from: String): Boolean {
        if (from.isEmpty()) return false
        return SettingUtils.appendLocationApps.split("\n").any { it.trim() == from }
    }

    //该来源应用的消息是否会在末尾自动附带位置
    fun willAppendLocation(from: String): Boolean {
        return SettingUtils.enableAppendLocation && appendLocationEnabledFor(from)
    }

    //按需刷新定位：缓存够新就直接复用，否则发起一次单次定位并等待（超时回退缓存）。
    //只在后台线程调用；主线程调用直接跳过，避免阻塞 UI 造成 ANR。
    fun ensureFreshLocation() {
        if (!SettingUtils.enableLocation) return
        if (Looper.myLooper() == Looper.getMainLooper()) return
        val collect = parseCollectTime(apiLocationCache.time)
        if (collect != null && System.currentTimeMillis() - collect < CACHE_FRESH_MS) return
        LocationService.requestLocationOnce(ON_DEMAND_TIMEOUT_MS)
    }

    //转发消息末尾附带最新位置：需要时按需单次定位刷新缓存，不做常驻定位
    fun appendLocation(content: String, from: String, msgKey: String, style: LocationLinkStyle = LocationLinkStyle.PLAIN): String {
        if (content.isEmpty()) return content
        if (willAppendLocation(from)) ensureFreshLocation()
        val emit = shouldEmitLocation(from, msgKey)
        Log.d("LocationUtils", "appendLocation: from=$from emit=$emit")
        if (!emit) return content
        //先按旧水位算出移动距离，再推进水位，否则永远算出 0
        val mdProtect = style == LocationLinkStyle.MARKDOWN
        val locationText = buildLocationText(apiLocationCache, style, msgKey, mdProtect)
        markLocationEmitted(msgKey)
        markLocationReported(apiLocationCache, msgKey)
        //markdown 通道连续换行会被折叠，自动附带也要走哨兵换行，保证位置块逐行显示
        val sep = if (mdProtect) MD_LF else "\n"
        return content + sep + locationText
    }

    //双阈值节流：移动超距离 或 距上次透出超时长 才再次透出；同一条消息的多次渲染（多通道/重试）靠 msgKey 保持一致
    private fun shouldEmitLocation(from: String, msgKey: String): Boolean {
        if (!willAppendLocation(from)) return false
        if (msgKey == SettingUtils.appendLocationLastMsg) return true

        val intervalMin = SettingUtils.appendLocationIntervalMin
        val distanceM = SettingUtils.appendLocationDistanceM
        if (intervalMin <= 0 || distanceM <= 0) return true

        val lastTime = SettingUtils.appendLocationLastTime
        if (lastTime == 0L) return true
        if (System.currentTimeMillis() - lastTime >= intervalMin * 60000L) return true

        val location = apiLocationCache
        val ready = location.latitude != 0.0 || location.longitude != 0.0
        val lastReady = SettingUtils.appendLocationLastLat != 0f || SettingUtils.appendLocationLastLng != 0f
        //就绪状态变化（定位恢复/丢失）必透出一次，其余按距离判定
        if (ready != lastReady) return true
        if (!ready) return false
        val moved = distanceMeters(
            SettingUtils.appendLocationLastLat.toDouble(),
            SettingUtils.appendLocationLastLng.toDouble(),
            location.latitude,
            location.longitude
        )
        return moved >= distanceM
    }

    private fun markLocationEmitted(msgKey: String) {
        val location = apiLocationCache
        SettingUtils.appendLocationLastTime = System.currentTimeMillis()
        SettingUtils.appendLocationLastLat = location.latitude.toFloat()
        SettingUtils.appendLocationLastLng = location.longitude.toFloat()
        SettingUtils.appendLocationLastMsg = msgKey
    }

    //两点间球面距离（米），用于判断「位置有没有实质变化」，GPS 抖动几米不算变化
    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val dLat = radLat2 - radLat1
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(radLat1) * cos(radLat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    //位置块：经纬度（按通道形态带链接）+ 移动提醒 + 地址 + 采集时间，自动附带与 {{LOCATION}} 标签共用
    fun buildLocationText(location: LocationInfo, style: LocationLinkStyle = LocationLinkStyle.PLAIN, msgKey: String = "", mdProtect: Boolean = false): String {
        //mdProtect：钉钉 markdown 下用占位符标记换行，块内保持紧挨；是否留可见间隔由模板里的空行决定，不自动加
        val lf = if (mdProtect) MD_LF else "\n"
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            return getString(R.string.append_location_unready)
        }

        val gcj = wgs84ToGcj02(location.latitude, location.longitude)
        //固定用 Locale.US 格式化：部分语言环境会把小数点输出成逗号，拼进地图链接的 position 参数会失效
        val lngText = String.format(Locale.US, "%.6f", gcj[1])
        val latText = String.format(Locale.US, "%.6f", gcj[0])
        val url = String.format(getString(R.string.append_location_map_url), lngText, latText)
        //markdown/html 把链接藏进经纬度；明文通道无法内链，坐标行只留数值，URL 放到整段最下面单独一行
        val plain = style == LocationLinkStyle.PLAIN
        val coordText = when (style) {
            LocationLinkStyle.MARKDOWN -> "[$lngText,$latText]($url)"
            LocationLinkStyle.HTML -> "<a href=\"$url\">$lngText,$latText</a>"
            LocationLinkStyle.PLAIN -> "$lngText,$latText"
        }

        val lines = mutableListOf(String.format(getString(R.string.append_location_coord_line), coordText))
        locationMoveLine(location, msgKey)?.let { lines.add(it) }
        if (location.address.isNotEmpty()) {
            lines.add(String.format(getString(R.string.append_location_address_line), location.address))
        }
        lines.add(collectTimeText(location.time))
        if (plain) lines.add(url)
        return lines.joinToString(lf)
    }

    //相对上次已发送位置的移动提醒；msgKey 为空表示只是渲染描述（日志/toString），不掺提醒
    private fun locationMoveLine(location: LocationInfo, msgKey: String): String? {
        if (msgKey.isEmpty()) return null
        val threshold = SettingUtils.locationMoveDistanceM
        if (threshold <= 0) return null
        val meters = movedMeters(location, msgKey) ?: return null
        if (meters < threshold) return null
        return String.format(
            getString(R.string.append_location_moved_line),
            distanceText(meters),
            elapsedText(System.currentTimeMillis() - SettingUtils.locationMoveLastTime)
        )
    }

    //与上次已发送位置的距离；同一条消息多通道/重试复用首次算出的值，保证各通道文案一致
    private fun movedMeters(location: LocationInfo, msgKey: String): Int? {
        if (location.latitude == 0.0 && location.longitude == 0.0) return null
        if (msgKey.isNotEmpty() && msgKey == SettingUtils.locationMoveLastMsg) {
            val cached = SettingUtils.locationMoveLastMeters
            return if (cached >= 0) cached else null
        }
        val lastLat = SettingUtils.locationMoveLastLat
        val lastLng = SettingUtils.locationMoveLastLng
        //首次上报没有可比的落点，只由 markLocationReported 铺水位，不出提醒
        if (lastLat == 0f && lastLng == 0f) return null
        return distanceMeters(lastLat.toDouble(), lastLng.toDouble(), location.latitude, location.longitude).roundToInt()
    }

    //发出过一段真实坐标就推进水位，下一条位置消息据此判断「走远了没有」；定位未就绪时不动水位
    fun markLocationReported(location: LocationInfo, msgKey: String) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        val meters = movedMeters(location, msgKey)
        SettingUtils.locationMoveLastTime = System.currentTimeMillis()
        SettingUtils.locationMoveLastLat = location.latitude.toFloat()
        SettingUtils.locationMoveLastLng = location.longitude.toFloat()
        SettingUtils.locationMoveLastMsg = msgKey
        SettingUtils.locationMoveLastMeters = meters ?: -1
    }

    private fun distanceText(meters: Int): String {
        return if (meters < 1000) {
            String.format(getString(R.string.append_location_distance_meter), meters)
        } else {
            String.format(getString(R.string.append_location_distance_km), String.format(Locale.US, "%.1f", meters / 1000.0))
        }
    }

    //采集时间 + 距今多久，让收到消息的人知道位置新鲜度
    private fun collectTimeText(collectTime: String): String {
        val millis = parseCollectTime(collectTime)
        if (millis == null) {
            return String.format(getString(R.string.append_location_time_only), collectTime)
        }
        return String.format(
            getString(R.string.append_location_time_line),
            collectTime,
            elapsedText(System.currentTimeMillis() - millis)
        )
    }

    private fun parseCollectTime(collectTime: String): Long? {
        if (collectTime.isEmpty()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(collectTime)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun elapsedText(elapsedMillis: Long): String {
        //时钟回拨或定位模块时间异常时按刚采集处理，避免出现「-3 分钟前」
        if (elapsedMillis < 60000L) return getString(R.string.append_location_ago_now)
        val minutes = elapsedMillis / 60000L
        if (minutes < 60L) return String.format(getString(R.string.append_location_ago_minute), minutes)
        val hours = minutes / 60L
        if (hours < 24L) return String.format(getString(R.string.append_location_ago_hour), hours)
        return String.format(getString(R.string.append_location_ago_day), hours / 24L)
    }

    //WGS-84 → GCJ-02，境外坐标不做偏移（偏移算法仅对国内范围定义）
    fun wgs84ToGcj02(wgLat: Double, wgLng: Double): DoubleArray {
        if (outOfChina(wgLat, wgLng)) return doubleArrayOf(wgLat, wgLng)

        var dLat = transformLat(wgLng - 105.0, wgLat - 35.0)
        var dLng = transformLng(wgLng - 105.0, wgLat - 35.0)
        val radLat = wgLat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (SEMI_MAJOR_AXIS / sqrtMagic * cos(radLat) * PI)
        return doubleArrayOf(wgLat + dLat, wgLng + dLng)
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return !(lng > 72.004 && lng < 137.8347 && lat > 0.8293 && lat < 55.8271)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 10.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}

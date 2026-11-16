package cn.ppps.forwarder.entity

import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.LocationUtils
import com.xuexiang.xutil.resource.ResUtils.getString
import java.io.Serializable
import java.util.Locale

data class LocationInfo(
    var longitude: Double = 0.0,
    var latitude: Double = 0.0,
    var address: String = "",
    var time: String = "",
    var provider: String = ""
) : Serializable {

    override fun toString(): String {
        if (latitude == 0.0 && longitude == 0.0) {
            return "\n" + getString(R.string.append_location_unready) + "\n"
        }
        //合行坐标与地图链接统一用 GCJ-02，与 append 尾巴保持一致
        val gcj = LocationUtils.wgs84ToGcj02(latitude, longitude)
        val lngText = String.format(Locale.US, "%.6f", gcj[1])
        val latText = String.format(Locale.US, "%.6f", gcj[0])
        var msg = "\n" + String.format(getString(R.string.append_location_coord_line), lngText, latText)
        if (address != "") msg += "\n" + String.format(getString(R.string.location_address), address)
        if (time != "") msg += "\n" + String.format(getString(R.string.location_time), time)
        if (provider != "") msg += "\n" + String.format(getString(R.string.location_provider), provider)
        msg += "\n" + String.format(getString(R.string.append_location_map_url), lngText, latText)
        return msg + "\n"
    }

}

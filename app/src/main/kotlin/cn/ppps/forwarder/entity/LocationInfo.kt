package cn.ppps.forwarder.entity

import cn.ppps.forwarder.utils.LocationUtils
import java.io.Serializable

data class LocationInfo(
    var longitude: Double = 0.0,
    var latitude: Double = 0.0,
    var address: String = "",
    var time: String = "",
    var provider: String = ""
) : Serializable {

    //日志与任务描述用的纯描述：不带 msgKey，因此不会掺进「移动提醒」那一行
    override fun toString(): String {
        return "\n" + LocationUtils.buildLocationText(this) + "\n"
    }

}

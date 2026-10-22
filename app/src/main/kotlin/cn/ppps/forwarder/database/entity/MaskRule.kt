package cn.ppps.forwarder.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.STATUS_ON
import com.xuexiang.xutil.resource.ResUtils.getString
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * 通道级打码规则：正则匹配发送内容后替换为指定文本，多条规则按 sort 升序依次生效
 */
@Parcelize
@Entity(tableName = "MaskRule")
data class MaskRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "name", defaultValue = "") var name: String = "",
    @ColumnInfo(name = "regex", defaultValue = "") var regex: String = "",
    @ColumnInfo(name = "replacement", defaultValue = "") var replacement: String = "",
    @ColumnInfo(name = "enable", defaultValue = "1") var enable: Int = STATUS_ON,
    @ColumnInfo(name = "sort", defaultValue = "0") var sort: Int = 0,
    @ColumnInfo(name = "time") var time: Date = Date(),
) : Parcelable {

    //replacement 留空即删除匹配到的内容；正则语法在保存时已校验过
    fun mask(content: String): String {
        return content.replace(Regex(regex), replacement)
    }

    fun buildDescription(): String {
        return "$regex → " + replacement.ifEmpty { getString(R.string.mask_rule_delete_matched) }
    }

}

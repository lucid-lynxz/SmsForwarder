package cn.ppps.forwarder.utils

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.workers.SendWorker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * 短信轮询兜底转发。
 * 部分定制系统（如 HyperOS 的验证码保护）不把验证码/通知类短信的广播发给非默认短信应用，
 * 但短信数据库里读得到，于是定时读库把广播漏掉的短信补进转发流程。
 * 与广播路径共用一份「最近已处理」记录去重，避免同一条短信转发两次。
 */
object SmsPollUtils {

    private const val TAG = "SmsPollUtils"

    //observer 事件驱动为主，轮询仅作低频兜底（防 observer 偶发漏触发）
    const val POLL_INTERVAL_MS = 300_000L
    private const val SEEN_TTL_MS = 90_000L
    private const val SEEN_MAX = 64
    private const val QUERY_LIMIT = 50

    data class SeenSms(val time: Long, val key: String)

    fun keyOf(address: String, body: String): String = "$address|$body"

    fun prune(list: MutableList<SeenSms>, now: Long): MutableList<SeenSms> {
        val kept = list.filter { now - it.time <= SEEN_TTL_MS }.takeLast(SEEN_MAX).toMutableList()
        list.clear()
        list.addAll(kept)
        return list
    }

    @Synchronized
    fun isRecentlySeen(address: String, body: String): Boolean {
        val now = System.currentTimeMillis()
        val list = readSeen()
        prune(list, now)
        return list.any { it.key == keyOf(address, body) }
    }

    @Synchronized
    fun markSeen(address: String, body: String) {
        val now = System.currentTimeMillis()
        val list = readSeen()
        prune(list, now)
        list.add(SeenSms(now, keyOf(address, body)))
        SettingUtils.smsPollSeen = Gson().toJson(list)
    }

    private fun readSeen(): MutableList<SeenSms> {
        val json = SettingUtils.smsPollSeen
        if (json.isEmpty()) return mutableListOf()
        return try {
            Gson().fromJson(json, object : TypeToken<MutableList<SeenSms>>() {}.type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun pollOnce(context: Context, advanceWatermark: Boolean = true) {
        val now = System.currentTimeMillis()
        val last = SettingUtils.smsPollLastDate
        //水位每次心跳都推进，开关关闭期间收到的短信不会在重新开启后积压补发；
        //事件驱动（observer）调用时不推进，避免行尚未可见就把水位推过短信时间导致漏发
        if (advanceWatermark) SettingUtils.smsPollLastDate = now
        if (last <= 0L) return
        if (!SettingUtils.enableSms || !SettingUtils.enableSmsPolling || SettingUtils.enablePureClientMode) return

        //getSmsInfoList 按时间倒序，取前缀里比水位新的部分再反转成正序
        val recent = PhoneUtils.getSmsInfoList(1, QUERY_LIMIT, 0, "")
        val fresh = recent.takeWhile { it.date > last }.reversed()
        if (fresh.isEmpty()) return

        for (sms in fresh) {
            if (sms.content.startsWith("smsf#")) continue
            if (isRecentlySeen(sms.number, sms.content)) continue
            val simInfo = when (sms.simId) {
                0 -> "SIM1_" + SettingUtils.extraSim1
                1 -> "SIM2_" + SettingUtils.extraSim2
                else -> ""
            }
            val msgInfo = MsgInfo("sms", sms.number, sms.content, Date(sms.date), simInfo, sms.simId, sms.subId)
            Log.d(TAG, "poll forward: from = ${sms.number}, date = ${sms.date}")
            val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(
                workDataOf(Worker.SEND_MSG_INFO to Gson().toJson(msgInfo))
            ).build()
            WorkManager.getInstance(context).enqueue(request)
            markSeen(sms.number, sms.content)
        }

        //事件驱动不把水位推到 now（防行尚未可见就推过短信时间导致漏发），
        //但推进到已处理的最大短信时间，避免低频兜底轮询在去重 TTL 过期后重复转发同一条
        if (!advanceWatermark && fresh.isNotEmpty()) {
            SettingUtils.smsPollLastDate = maxOf(last, fresh.maxOf { it.date })
        }
    }
}

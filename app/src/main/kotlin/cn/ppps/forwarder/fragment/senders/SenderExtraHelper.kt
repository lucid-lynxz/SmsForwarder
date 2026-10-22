package cn.ppps.forwarder.fragment.senders

import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.database.entity.TimeWindow
import cn.ppps.forwarder.databinding.IncludeSenderExtraBinding
import com.xuexiang.xui.widget.flowlayout.FlowTagLayout
import com.xuexiang.xutil.resource.ResUtils.getString

/**
 * 发送通道编辑页的公共扩展项（允许转发时间窗口 / 内容打码规则），供 16 种通道类型复用
 */
class SenderExtraHelper(binding: IncludeSenderExtraBinding) {

    private val timeWindowList: List<TimeWindow> = Core.timeWindow.getAllNonCache()
    private val maskRuleList: List<MaskRule> = Core.maskRule.getAllNonCache()
    private val spinner = binding.spTimeWindow
    private val tagLayout = binding.flowlayoutMaskRule
    private var selectedPositions: List<Int> = emptyList()

    init {
        spinner.setItems(listOf(getString(R.string.time_window_unlimited)) + timeWindowList.map { it.name })
        spinner.selectedIndex = 0

        if (maskRuleList.isNotEmpty()) {
            tagLayout.setTagCheckedMode(FlowTagLayout.FLOW_TAG_CHECKED_MULTI)
            tagLayout.setItems(maskRuleList.map { it.name })
            tagLayout.setOnTagSelectListener { _, _, list -> selectedPositions = list ?: emptyList() }
        }
    }

    fun bind(sender: Sender) {
        val index = timeWindowList.indexOfFirst { it.id == sender.timeWindowId }
        spinner.selectedIndex = if (index >= 0) index + 1 else 0

        //无打码规则时标签未初始化，去设置选中态会空指针
        if (maskRuleList.isEmpty()) return
        val ids = sender.maskRuleIds.split(",").mapNotNull { it.trim().toLongOrNull() }
        val positions = maskRuleList.mapIndexedNotNull { position, rule ->
            if (ids.contains(rule.id)) position else null
        }
        selectedPositions = positions
        tagLayout.setSelectedPositions(positions)
    }

    fun applyTo(sender: Sender) {
        sender.timeWindowId = selectedTimeWindowId()
        sender.maskRuleIds = selectedMaskRuleIds()
    }

    private fun selectedTimeWindowId(): Long {
        //下拉项 0 为「不限」，其余依次对应 timeWindowList
        val index = spinner.selectedIndex
        return if (index <= 0 || index > timeWindowList.size) 0L else timeWindowList[index - 1].id
    }

    //标签按 maskRuleList 顺序生成，取顺序即天然按规则的生效顺序排列
    private fun selectedMaskRuleIds(): String =
        maskRuleList.filterIndexed { index, _ -> selectedPositions.contains(index) }
            .joinToString(",") { it.id.toString() }

}

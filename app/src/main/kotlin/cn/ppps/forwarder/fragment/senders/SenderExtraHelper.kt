package cn.ppps.forwarder.fragment.senders

import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.databinding.IncludeSenderExtraBinding
import com.xuexiang.xui.widget.flowlayout.FlowTagLayout

/**
 * 发送通道编辑页的公共扩展项（内容打码规则），供 16 种通道类型复用
 */
class SenderExtraHelper(binding: IncludeSenderExtraBinding) {

    private val maskRuleList: List<MaskRule> = Core.maskRule.getAllNonCache()
    private val tagLayout = binding.flowlayoutMaskRule
    private var selectedPositions: List<Int> = emptyList()

    init {
        if (maskRuleList.isNotEmpty()) {
            tagLayout.setTagCheckedMode(FlowTagLayout.FLOW_TAG_CHECKED_MULTI)
            tagLayout.setItems(maskRuleList.map { it.name })
            tagLayout.setOnTagSelectListener { _, _, list -> selectedPositions = list ?: emptyList() }
        }
    }

    fun bind(sender: Sender) {
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
        sender.maskRuleIds = selectedMaskRuleIds()
    }

    //标签按 maskRuleList 顺序生成，取顺序即天然按规则的生效顺序排列
    private fun selectedMaskRuleIds(): String =
        maskRuleList.filterIndexed { index, _ -> selectedPositions.contains(index) }
            .joinToString(",") { it.id.toString() }

}

package cn.ppps.forwarder.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import com.alibaba.android.vlayout.VirtualLayoutManager
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.MaskRulePagingAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.MaskRuleViewModel
import cn.ppps.forwarder.databinding.FragmentMaskRulesBinding
import cn.ppps.forwarder.utils.KEY_MASK_RULE_ID
import cn.ppps.forwarder.utils.KEY_MASK_RULE_TYPE
import cn.ppps.forwarder.utils.MASK_RULE_TYPE_ALL
import cn.ppps.forwarder.utils.XToastUtils
import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xpage.base.XPageActivity
import com.xuexiang.xpage.core.PageOption
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.utils.ThemeUtils
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Page(name = "内容打码规则")
class MaskRulesFragment : BaseFragment<FragmentMaskRulesBinding?>(), MaskRulePagingAdapter.OnItemClickListener {

    private var titleBar: TitleBar? = null
    private var adapter = MaskRulePagingAdapter(this)
    private val viewModel by viewModels<MaskRuleViewModel> { BaseViewModelFactory(context) }

    //姓名马赛克入口只展示字符串类型规则
    @JvmField
    @AutoWired(name = KEY_MASK_RULE_TYPE)
    var maskRuleType: Int = MASK_RULE_TYPE_ALL

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentMaskRulesBinding {
        return FragmentMaskRulesBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setTitle(if (isStringMask()) R.string.menu_string_mask else R.string.menu_mask_rule)
        titleBar!!.setActionTextColor(ThemeUtils.resolveColor(context, R.attr.colorAccent))
        titleBar!!.addAction(object : TitleBar.ImageAction(R.drawable.ic_add) {
            @SingleClick
            override fun performAction(view: View) {
                val page = if (isStringMask()) StringMaskBatchFragment::class.java else MaskRuleEditFragment::class.java
                PageOption.to(page).setNewActivity(true).open((context as XPageActivity?)!!)
            }
        })
        return titleBar
    }

    private fun isStringMask(): Boolean = maskRuleType == MaskRule.MASK_TYPE_STRING

    private fun maskRuleFlow(): Flow<PagingData<MaskRule>> {
        return if (maskRuleType == MASK_RULE_TYPE_ALL) viewModel.allMaskRule else viewModel.maskRuleByType(maskRuleType)
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        val virtualLayoutManager = VirtualLayoutManager(requireContext())
        binding!!.recyclerView.layoutManager = virtualLayoutManager
        val viewPool = RecycledViewPool()
        binding!!.recyclerView.setRecycledViewPool(viewPool)
        viewPool.setMaxRecycledViews(0, 10)
        binding!!.recyclerView.adapter = adapter
    }

    override fun initListeners() {
        //Room 活流：收集一次就会随数据库变动自动更新，不必进页面时播一遍下拉动画来拉数据
        lifecycleScope.launch {
            maskRuleFlow().collectLatest { adapter.submitData(it) }
        }

        //下拉刷新
        binding!!.refreshLayout.setOnRefreshListener { refreshLayout: RefreshLayout ->
            adapter.refresh()
            refreshLayout.finishRefresh()
        }
    }

    override fun onItemClicked(view: View?, item: MaskRule) {
        when (view?.id) {
            R.id.iv_edit -> {
                PageOption.to(MaskRuleEditFragment::class.java)
                    .setNewActivity(true)
                    .putLong(KEY_MASK_RULE_ID, item.id)
                    .open(this)
            }

            R.id.iv_delete -> {
                MaterialDialog.Builder(requireContext())
                    .title(R.string.delete_mask_rule_title)
                    .content(R.string.delete_mask_rule_tips)
                    .positiveText(R.string.lab_yes)
                    .negativeText(R.string.lab_no)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        viewModel.delete(item)
                        adapter.refresh()
                        XToastUtils.success(R.string.delete_mask_rule_toast)
                    }
                    .show()
            }

            else -> {}
        }
    }

    override fun onItemRemove(view: View?, id: Int) {}
}

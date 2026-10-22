package cn.ppps.forwarder.fragment

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.MaskRuleViewModel
import cn.ppps.forwarder.databinding.FragmentMaskRuleEditBinding
import cn.ppps.forwarder.utils.KEY_MASK_RULE_ID
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.STATUS_OFF
import cn.ppps.forwarder.utils.STATUS_ON
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import java.util.Date

@Page(name = "内容打码规则·编辑器")
class MaskRuleEditFragment : BaseFragment<FragmentMaskRuleEditBinding?>(), View.OnClickListener {

    private val TAG: String = MaskRuleEditFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private val viewModel by viewModels<MaskRuleViewModel> { BaseViewModelFactory(context) }

    @JvmField
    @AutoWired(name = KEY_MASK_RULE_ID)
    var maskRuleId: Long = 0

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentMaskRuleEditBinding {
        return FragmentMaskRuleEditBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setTitle(R.string.menu_mask_rule)
        return titleBar
    }

    override fun initViews() {
        if (maskRuleId <= 0) { //新增
            titleBar?.setSubTitle(getString(R.string.add_mask_rule))
            binding!!.btnDel.setText(R.string.discard)
            return
        }

        //编辑
        binding!!.btnDel.setText(R.string.del)
        val maskRule = Core.maskRule.getOne(maskRuleId)
        if (maskRule == null) {
            XToastUtils.error(R.string.mask_rule_missing)
            popToBack()
            return
        }
        titleBar?.setSubTitle(getString(R.string.edit_mask_rule) + ": " + maskRule.name)
        binding!!.etName.setText(maskRule.name)
        binding!!.etRegex.setText(maskRule.regex)
        binding!!.etReplacement.setText(maskRule.replacement)
        binding!!.etSort.setText(maskRule.sort.toString())
        binding!!.sbEnable.isChecked = maskRule.enable == STATUS_ON
    }

    override fun initListeners() {
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_del -> {
                    if (maskRuleId <= 0) {
                        popToBack()
                        return
                    }

                    MaterialDialog.Builder(requireContext())
                        .title(R.string.delete_mask_rule_title)
                        .content(R.string.delete_mask_rule_tips)
                        .positiveText(R.string.lab_yes)
                        .negativeText(R.string.lab_no)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            viewModel.delete(Core.maskRule.getOne(maskRuleId)!!)
                            XToastUtils.success(R.string.delete_mask_rule_toast)
                            popToBack()
                        }
                        .show()
                    return
                }

                R.id.btn_save -> {
                    val maskRuleNew = checkForm()
                    Log.d(TAG, maskRuleNew.toString())
                    if (maskRuleId <= 0) {
                        viewModel.insert(maskRuleNew)
                    } else {
                        viewModel.update(maskRuleNew)
                    }
                    XToastUtils.success(R.string.tipSaveSuccess)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString())
            e.printStackTrace()
            Log.e(TAG, "onClick error:$e")
        }
    }

    private fun checkForm(): MaskRule {
        val name = binding!!.etName.text.toString().trim()
        if (TextUtils.isEmpty(name)) {
            throw Exception(getString(R.string.invalid_name))
        }
        val regex = binding!!.etRegex.text.toString().trim()
        if (TextUtils.isEmpty(regex)) {
            throw Exception(getString(R.string.invalid_mask_rule_regex))
        }
        //正则写错时替换会直接抛异常，这里提前拦住，避免发送时才失败
        try {
            Regex(regex)
        } catch (e: Exception) {
            throw Exception(getString(R.string.invalid_mask_rule_regex) + ": " + e.message)
        }
        val replacement = binding!!.etReplacement.text.toString()
        val sort = binding!!.etSort.text.toString().trim().toIntOrNull() ?: 0
        return MaskRule(
            maskRuleId,
            name,
            regex,
            replacement,
            if (binding!!.sbEnable.isChecked) STATUS_ON else STATUS_OFF,
            sort,
            Date()
        )
    }

}

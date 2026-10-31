package cn.ppps.forwarder.fragment

import android.text.Editable
import android.text.TextWatcher
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
import cn.ppps.forwarder.databinding.FragmentStringMaskBatchBinding
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.PinyinUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar

/**
 * 姓名马赛克批量录入：一次粘贴一批姓名，默认按拼音首字母生成替换文本，
 * 落库为字符串类型的打码规则，之后可在列表里逐条修改替换结果。
 */
@Page(name = "姓名马赛克·批量添加")
class StringMaskBatchFragment : BaseFragment<FragmentStringMaskBatchBinding?>(), View.OnClickListener {

    private val TAG: String = StringMaskBatchFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private val viewModel by viewModels<MaskRuleViewModel> { BaseViewModelFactory(context) }

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentStringMaskBatchBinding {
        return FragmentStringMaskBatchBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setTitle(R.string.menu_string_mask)
        titleBar!!.setSubTitle(getString(R.string.add_string_mask))
        return titleBar
    }

    override fun initViews() {
        refreshPreview()
    }

    override fun initListeners() {
        binding!!.btnSave.setOnClickListener(this)
        binding!!.etNames.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                refreshPreview()
            }
        })
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            if (v.id != R.id.btn_save) return

            val names = parseNames()
            if (names.isEmpty()) {
                XToastUtils.error(R.string.string_mask_empty)
                return
            }

            val all = Core.maskRule.getAllNonCache()
            val existed = all.filter { it.type == MaskRule.MASK_TYPE_STRING }.map { it.regex }.toSet()
            var sort = (all.maxOfOrNull { it.sort } ?: 0) + 1
            //长姓名先生效，否则「王芳」会先把「王芳芳」打断
            val news = names.filter { !existed.contains(it) }
                .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
                .map {
                    MaskRule(
                        name = it,
                        regex = it,
                        replacement = PinyinUtils.initials(it),
                        type = MaskRule.MASK_TYPE_STRING,
                        sort = sort++,
                    )
                }

            Log.d(TAG, "批量添加姓名打码: ${news.map { it.name }}")
            viewModel.insertAll(news)
            XToastUtils.success(getString(R.string.string_mask_added, news.size, names.size - news.size))
            popToBack()
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString())
            e.printStackTrace()
            Log.e(TAG, "onClick error:$e")
        }
    }

    private fun parseNames(): List<String> {
        return binding!!.etNames.text.toString()
            .split(NAME_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun refreshPreview() {
        val names = parseNames()
        binding!!.tvPreview.text = if (names.isEmpty()) {
            getString(R.string.string_mask_no_valid_names)
        } else {
            names.take(PREVIEW_LIMIT).joinToString("\n") { "$it → ${PinyinUtils.initials(it)}" }
        }
    }

    companion object {
        private val NAME_SEPARATOR = Regex("[,，、;；\\s]+")
        private const val PREVIEW_LIMIT = 30
    }

}

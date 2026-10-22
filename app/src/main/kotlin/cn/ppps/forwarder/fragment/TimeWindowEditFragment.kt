package cn.ppps.forwarder.fragment

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.TimeWindow
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.TimeWindowViewModel
import cn.ppps.forwarder.databinding.FragmentTimeWindowEditBinding
import cn.ppps.forwarder.utils.DataProvider
import cn.ppps.forwarder.utils.KEY_TIME_WINDOW_ID
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.button.SmoothCheckBox
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xui.widget.picker.widget.builder.OptionsPickerBuilder
import com.xuexiang.xui.widget.picker.widget.listener.OnOptionsSelectListener
import java.util.Calendar
import java.util.Date

@Page(name = "允许转发时间窗口·编辑器")
class TimeWindowEditFragment : BaseFragment<FragmentTimeWindowEditBinding?>(), View.OnClickListener {

    private val TAG: String = TimeWindowEditFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private val viewModel by viewModels<TimeWindowViewModel> { BaseViewModelFactory(context) }

    private val mTimeOption = DataProvider.timePeriodOption
    private var periodStartIndex = 0
    private var periodEndIndex = mTimeOption.size - 1

    @JvmField
    @AutoWired(name = KEY_TIME_WINDOW_ID)
    var timeWindowId: Long = 0

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentTimeWindowEditBinding {
        return FragmentTimeWindowEditBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setTitle(R.string.menu_time_window)
        return titleBar
    }

    override fun initViews() {
        binding!!.tvPeriod.text = mTimeOption[periodStartIndex] + " ~ " + mTimeOption[periodEndIndex]

        if (timeWindowId <= 0) { //新增
            titleBar?.setSubTitle(getString(R.string.add_time_window))
            binding!!.btnDel.setText(R.string.discard)
            return
        }

        //编辑
        binding!!.btnDel.setText(R.string.del)
        val timeWindow = Core.timeWindow.getOne(timeWindowId)
        if (timeWindow == null) {
            XToastUtils.error(R.string.time_window_missing)
            popToBack()
            return
        }
        titleBar?.setSubTitle(getString(R.string.edit_time_window) + ": " + timeWindow.name)
        binding!!.etName.setText(timeWindow.name)
        periodStartIndex = mTimeOption.indexOf(timeWindow.periodStart).takeIf { it >= 0 } ?: 0
        periodEndIndex = mTimeOption.indexOf(timeWindow.periodEnd).takeIf { it >= 0 } ?: mTimeOption.size - 1
        binding!!.tvPeriod.text = mTimeOption[periodStartIndex] + " ~ " + mTimeOption[periodEndIndex]

        val days = timeWindow.dayOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        getDayCheckBoxMap().forEach { (day, checkBox) ->
            checkBox.isChecked = days.contains(day)
        }
    }

    override fun initListeners() {
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        binding!!.btnPeriod.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_period -> {
                    OptionsPickerBuilder(context, OnOptionsSelectListener { _: View?, options1: Int, options2: Int, _: Int ->
                        periodStartIndex = options1
                        periodEndIndex = options2
                        val txt = mTimeOption[options1] + " ~ " + mTimeOption[options2]
                        binding!!.tvPeriod.text = txt
                        XToastUtils.toast(txt)
                        return@OnOptionsSelectListener false
                    }).setTitleText(getString(R.string.select_time_period)).setSelectOptions(periodStartIndex, periodEndIndex).build<Any>().also {
                        it.setNPicker(mTimeOption, mTimeOption)
                        it.show()
                    }
                }

                R.id.btn_del -> {
                    if (timeWindowId <= 0) {
                        popToBack()
                        return
                    }

                    MaterialDialog.Builder(requireContext())
                        .title(R.string.delete_time_window_title)
                        .content(R.string.delete_time_window_tips)
                        .positiveText(R.string.lab_yes)
                        .negativeText(R.string.lab_no)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            viewModel.delete(Core.timeWindow.getOne(timeWindowId)!!)
                            XToastUtils.success(R.string.delete_time_window_toast)
                            popToBack()
                        }
                        .show()
                    return
                }

                R.id.btn_save -> {
                    val timeWindowNew = checkForm()
                    Log.d(TAG, timeWindowNew.toString())
                    if (timeWindowId <= 0) {
                        viewModel.insert(timeWindowNew)
                    } else {
                        viewModel.update(timeWindowNew)
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

    private fun checkForm(): TimeWindow {
        val name = binding!!.etName.text.toString().trim()
        if (TextUtils.isEmpty(name)) {
            throw Exception(getString(R.string.invalid_name))
        }
        val days = getDayCheckBoxMap().filter { it.value.isChecked }.toList().map { it.first }.joinToString(",")
        return TimeWindow(
            timeWindowId,
            name,
            days,
            mTimeOption[periodStartIndex],
            mTimeOption[periodEndIndex],
            Date()
        )
    }

    private fun getDayCheckBoxMap(): Map<Int, SmoothCheckBox> {
        return mapOf(
            Calendar.SUNDAY to binding!!.sun,
            Calendar.MONDAY to binding!!.mon,
            Calendar.TUESDAY to binding!!.tue,
            Calendar.WEDNESDAY to binding!!.wed,
            Calendar.THURSDAY to binding!!.thu,
            Calendar.FRIDAY to binding!!.fri,
            Calendar.SATURDAY to binding!!.sat,
        )
    }

}

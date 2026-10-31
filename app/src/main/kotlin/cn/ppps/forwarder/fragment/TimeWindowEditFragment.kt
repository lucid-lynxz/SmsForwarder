package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.TimePeriod
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
    //新建窗口默认「全天」，与旧版单时间段默认一致
    private val periodList: MutableList<TimePeriod> = mutableListOf(TimePeriod(mTimeOption.first(), mTimeOption.last()))

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
        renderPeriods()

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
        periodList.clear()
        periodList.addAll(timeWindow.periodList)
        renderPeriods()

        val days = timeWindow.dayOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        getDayCheckBoxMap().forEach { (day, checkBox) ->
            checkBox.isChecked = days.contains(day)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderPeriods() {
        val container = binding!!.llPeriods
        container.removeAllViews()
        periodList.forEachIndexed { index, period ->
            val view = LayoutInflater.from(context).inflate(R.layout.item_time_window_period, container, false)
            view.findViewById<TextView>(R.id.tv_period).text = period.toString()
            view.findViewById<View>(R.id.btn_period).setOnClickListener { showPeriodPicker(index) }
            val delButton = view.findViewById<View>(R.id.btn_period_del)
            //至少保留一个时间段，全部删完等价于不限时段，容易误操作
            delButton.visibility = if (periodList.size > 1) View.VISIBLE else View.INVISIBLE
            delButton.setOnClickListener {
                periodList.removeAt(index)
                renderPeriods()
            }
            container.addView(view)
        }
    }

    private fun showPeriodPicker(index: Int) {
        val period = periodList[index]
        val startIndex = mTimeOption.indexOf(period.start).takeIf { it >= 0 } ?: 0
        val endIndex = mTimeOption.indexOf(period.end).takeIf { it >= 0 } ?: mTimeOption.size - 1
        OptionsPickerBuilder(context, OnOptionsSelectListener { _: View?, options1: Int, options2: Int, _: Int ->
            periodList[index] = TimePeriod(mTimeOption[options1], mTimeOption[options2])
            renderPeriods()
            return@OnOptionsSelectListener false
        }).setTitleText(getString(R.string.select_time_period)).setSelectOptions(startIndex, endIndex).build<Any>().also {
            it.setNPicker(mTimeOption, mTimeOption)
            it.show()
        }
    }

    override fun initListeners() {
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        binding!!.btnPeriodAdd.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_period_add -> {
                    periodList.add(TimePeriod(mTimeOption.first(), mTimeOption.last()))
                    renderPeriods()
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
        if (TimeWindow.hasOverlappedPeriods(periodList)) {
            throw Exception(getString(R.string.time_window_period_overlapped))
        }
        val days = getDayCheckBoxMap().filter { it.value.isChecked }.toList().map { it.first }.joinToString(",")
        return TimeWindow(
            timeWindowId,
            name,
            days,
            periodList.first().start,
            periodList.first().end,
            TimeWindow.periodsToString(periodList),
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

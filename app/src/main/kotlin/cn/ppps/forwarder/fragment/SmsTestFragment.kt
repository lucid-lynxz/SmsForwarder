package cn.ppps.forwarder.fragment

import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.vlayout.DelegateAdapter
import com.alibaba.android.vlayout.VirtualLayoutManager
import com.alibaba.android.vlayout.layout.LinearLayoutHelper
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.base.delegate.SimpleDelegateAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentSmsTestBinding
import cn.ppps.forwarder.entity.SmsInfo
import cn.ppps.forwarder.utils.PhoneUtils
import cn.ppps.forwarder.utils.SmsCodeUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.adapter.recyclerview.RecyclerViewHolder
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xutil.data.DateUtils
import com.xuexiang.xutil.resource.ResUtils.getStringArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Page(name = "短信读取测试")
class SmsTestFragment : BaseFragment<FragmentSmsTestBinding?>(), View.OnClickListener {

    private var mAdapter: SimpleDelegateAdapter<SmsInfo>? = null
    private var smsType: Int = 1
    private var onlyCode: Boolean = false
    private var readList: MutableList<SmsInfo> = mutableListOf()

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSmsTestBinding {
        return FragmentSmsTestBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar {
        val titleBar = super.initTitle()!!.setImmersive(false)
        titleBar.setTitle(R.string.sms_read_test)
        return titleBar
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        val virtualLayoutManager = VirtualLayoutManager(requireContext())
        binding!!.recyclerView.layoutManager = virtualLayoutManager
        val viewPool = RecyclerView.RecycledViewPool()
        binding!!.recyclerView.setRecycledViewPool(viewPool)
        viewPool.setMaxRecycledViews(0, 10)

        mAdapter = object : SimpleDelegateAdapter<SmsInfo>(
            R.layout.adapter_sms_test_list_item,
            LinearLayoutHelper()
        ) {
            override fun bindData(
                holder: RecyclerViewHolder,
                position: Int,
                item: SmsInfo,
            ) {
                holder.text(R.id.tv_from, item.number)
                holder.text(R.id.tv_time, DateUtils.getFriendlyTimeSpanByNow(item.date))
                holder.image(R.id.iv_sim_image, item.simImageId)
                holder.text(R.id.tv_content, item.content)
                holder.visible(R.id.tv_tag, if (SmsCodeUtils.looksLikeCode(item.content)) View.VISIBLE else View.GONE)
            }
        }

        val delegateAdapter = DelegateAdapter(virtualLayoutManager)
        delegateAdapter.addAdapter(mAdapter)
        binding!!.recyclerView.adapter = delegateAdapter

        binding!!.tabBar.setTabTitles(getStringArray(R.array.sms_type_option))
        binding!!.tabBar.setOnTabClickListener { _, position ->
            smsType = position + 1
            loadData()
        }

        binding!!.sbOnlyCode.setOnCheckedChangeListener { _, isChecked ->
            onlyCode = isChecked
            refreshList()
        }
    }

    override fun initListeners() {
        binding!!.btnGrantPermission.setOnClickListener(this)
        //下拉刷新：真正重查一次短信数据库
        binding!!.refreshLayout.setOnRefreshListener {
            loadData()
        }
        loadData()
    }

    @SingleClick
    override fun onClick(v: View) {
        if (v.id == R.id.btnGrantPermission) {
            requestReadSmsPermission()
        }
    }

    private fun requestReadSmsPermission() {
        XXPermissions.with(this)
            .permission(PermissionLists.getReadSmsPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                    if (deniedList.isEmpty()) {
                        XToastUtils.info(R.string.toast_granted_all)
                    } else {
                        if (XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)) {
                            XToastUtils.error(R.string.toast_denied_never)
                            XXPermissions.startPermissionActivity(requireContext(), deniedList)
                        }
                        XToastUtils.warning(getString(R.string.toast_granted_part))
                    }
                    loadData()
                }
            })
    }

    private fun showStatus() {
        val granted = XXPermissions.isGrantedPermission(requireContext(), PermissionLists.getReadSmsPermission())
        binding!!.tvPermission.text = getString(
            R.string.sms_test_permission,
            getString(if (granted) R.string.sms_test_permission_granted else R.string.sms_test_permission_denied)
        )
        binding!!.btnGrantPermission.visibility = if (granted) View.GONE else View.VISIBLE

        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(requireContext())
        val defaultApp = when {
            defaultSmsPackage.isNullOrEmpty() -> getString(R.string.sms_test_default_app_unknown)
            defaultSmsPackage == requireContext().packageName -> getString(R.string.sms_test_default_app_self)
            else -> defaultSmsPackage
        }
        binding!!.tvDefaultApp.text = getString(R.string.sms_test_default_app, defaultApp)
    }

    private fun loadData() {
        showStatus()
        lifecycleScope.launch {
            readList = withContext(Dispatchers.IO) { PhoneUtils.getSmsInfoList(smsType, MAX_READ_COUNT, 0, "") }
            refreshList()
            val codeCount = readList.count { SmsCodeUtils.looksLikeCode(it.content) }
            binding!!.tvResult.text = when {
                readList.isNotEmpty() -> getString(R.string.sms_test_result, readList.size, codeCount)
                !XXPermissions.isGrantedPermission(requireContext(), PermissionLists.getReadSmsPermission()) ->
                    getString(R.string.sms_test_permission_missing)

                else -> getString(R.string.sms_test_result_empty)
            }
            binding!!.refreshLayout.finishRefresh()
        }
    }

    private fun refreshList() {
        val list = if (onlyCode) readList.filter { SmsCodeUtils.looksLikeCode(it.content) } else readList
        mAdapter!!.refresh(list)
    }

    companion object {
        private const val MAX_READ_COUNT = 200
    }

}

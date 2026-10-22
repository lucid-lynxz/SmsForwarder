package cn.ppps.forwarder.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cn.ppps.forwarder.adapter.MaskRulePagingAdapter.MyViewHolder
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.databinding.AdapterMaskRulesCardViewListItemBinding
import cn.ppps.forwarder.utils.STATUS_ON

@Suppress("EmptyMethod")
class MaskRulePagingAdapter(private val itemClickListener: OnItemClickListener) : PagingDataAdapter<MaskRule, MyViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = AdapterMaskRulesCardViewListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            //禁用的规则整卡置灰，避免误以为仍在生效
            holder.binding.cardView.alpha = if (item.enable == STATUS_ON) 1f else 0.4f
            holder.binding.tvName.text = item.name
            holder.binding.tvDescription.text = item.buildDescription()
            holder.binding.ivEdit.setOnClickListener { view: View? ->
                itemClickListener.onItemClicked(view, item)
            }
            holder.binding.ivDelete.setOnClickListener { view: View? ->
                itemClickListener.onItemClicked(view, item)
            }
        }
    }

    class MyViewHolder(val binding: AdapterMaskRulesCardViewListItemBinding) : RecyclerView.ViewHolder(binding.root)
    interface OnItemClickListener {
        fun onItemClicked(view: View?, item: MaskRule)
        fun onItemRemove(view: View?, id: Int)
    }

    companion object {
        var diffCallback: DiffUtil.ItemCallback<MaskRule> = object : DiffUtil.ItemCallback<MaskRule>() {
            override fun areItemsTheSame(oldItem: MaskRule, newItem: MaskRule): Boolean {
                return oldItem.id == newItem.id
            }

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: MaskRule, newItem: MaskRule): Boolean {
                return oldItem === newItem
            }
        }
    }
}

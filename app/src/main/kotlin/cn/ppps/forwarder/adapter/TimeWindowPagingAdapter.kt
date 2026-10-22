package cn.ppps.forwarder.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cn.ppps.forwarder.adapter.TimeWindowPagingAdapter.MyViewHolder
import cn.ppps.forwarder.database.entity.TimeWindow
import cn.ppps.forwarder.databinding.AdapterTimeWindowsCardViewListItemBinding

@Suppress("EmptyMethod")
class TimeWindowPagingAdapter(private val itemClickListener: OnItemClickListener) : PagingDataAdapter<TimeWindow, MyViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = AdapterTimeWindowsCardViewListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
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

    class MyViewHolder(val binding: AdapterTimeWindowsCardViewListItemBinding) : RecyclerView.ViewHolder(binding.root)
    interface OnItemClickListener {
        fun onItemClicked(view: View?, item: TimeWindow)
        fun onItemRemove(view: View?, id: Int)
    }

    companion object {
        var diffCallback: DiffUtil.ItemCallback<TimeWindow> = object : DiffUtil.ItemCallback<TimeWindow>() {
            override fun areItemsTheSame(oldItem: TimeWindow, newItem: TimeWindow): Boolean {
                return oldItem.id == newItem.id
            }

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: TimeWindow, newItem: TimeWindow): Boolean {
                return oldItem === newItem
            }
        }
    }
}

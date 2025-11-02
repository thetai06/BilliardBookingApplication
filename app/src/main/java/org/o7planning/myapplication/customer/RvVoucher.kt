package org.o7planning.myapplication.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.data.dataVoucher
import org.o7planning.myapplication.databinding.ItemVoucherBinding

interface onClickVoucherListenner{
    fun onClickVoucher(voucher: String)
}

class RvVoucher(val list:List<dataVoucher>, private var listenner: onClickVoucherListenner): RecyclerView.Adapter<RvVoucher.viewHolderItem>() {

    inner class viewHolderItem(val binding: ItemVoucherBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): viewHolderItem {
        val binding = ItemVoucherBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(
        holder: viewHolderItem,
        position: Int
    ) {
        holder.binding.apply {
            val data = list[position]
            titleVoucher.text = data.description
            dateVoucher.text = "Thời gian: ${data.expiryDate}"
            voucherCode.text = data.code
            btnApplyVoucher.setOnClickListener {
                listenner.onClickVoucher(data.code.toString())
            }
        }

    }

    override fun getItemCount(): Int {
        return list.size
    }

}
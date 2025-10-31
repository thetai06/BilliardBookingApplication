package org.o7planning.myapplication.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.ItemTheBookingBinding

class RvTheBooking(val list: ArrayList<dataTableManagement>) : RecyclerView.Adapter<RvTheBooking.viewHolderItem>() {

    inner class viewHolderItem(val binding: ItemTheBookingBinding) : RecyclerView.ViewHolder(binding.root)

    var onClickCancelOrder: ((dataTableManagement, Int) -> Unit)? = null
    var onClickItemOrder: ((dataTableManagement,Int) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): viewHolderItem {
        val binding =
            ItemTheBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(
        holder: viewHolderItem,
        position: Int
    ) {
        val data = list[position]
        holder.binding.apply {
            if (data.phoneNumber == "null"){
                nameRealtime.text = "${data.name} - ${data.email}"
            }else{
                nameRealtime.text = "${data.name} - ${data.phoneNumber}"
            }
            timeRealtime.text = "Thời gian: ${data.startTime} - ${data.endTime}"
            address.text = data.addressClb
            personRealtime.text = "${data.person} Người"
            statusRealtime.text = data.paymentStatus
            manyRealtime.text = data.money.toString()
            statusRealtime.setText(data.paymentStatus)
            if (data.paymentStatus == "Đã thanh toán"){
                statusRealtime.setBackgroundResource(R.drawable.bg_status_green)
            }else if (data.paymentStatus =="Đang chơi"){
                statusRealtime.setBackgroundResource(R.drawable.bg_start_game)
            }else if (data.paymentStatus == "Chờ thanh toán VietQR" || data.paymentStatus == "Chờ thanh toán VNPay"){
                statusRealtime.setBackgroundResource(R.drawable.bg_status_yellow)
            }
        }
        holder.itemView.setOnLongClickListener {
            onClickCancelOrder?.invoke(data,position)
            return@setOnLongClickListener true
        }
        holder.itemView.setOnClickListener {
            onClickItemOrder?.invoke(data,position)
        }


    }

    override fun getItemCount(): Int {
        return list.size
    }


}
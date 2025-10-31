package org.o7planning.myapplication.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.ItemHistoryBinding

class RvHistory(val list: List<dataTableManagement>) :
    RecyclerView.Adapter<RvHistory.viewHolderItem>() {

    inner class viewHolderItem(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): viewHolderItem {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(
        holder: viewHolderItem,
        position: Int
    ) {
        val data = list[position]
        holder.binding.apply {
            if (data.phoneNumber == "null") {
                nameRealtime.text = "${data.name} - ${data.email}"
            } else {
                nameRealtime.text = "${data.name} - ${data.phoneNumber}"
            }
            timeRealtime.text = "Thời gian: ${data.startTime} - ${data.endTime}"
            address.text = data.addressClb
            personRealtime.text = "${data.person} Người"
            statusRealtime.text = data.paymentStatus
            manyRealtime.text = data.money.toString()
            statusRealtime.setText(data.paymentStatus)
            if (data.paymentStatus == "Đã hoàn thành") {
                statusRealtime.setBackgroundResource(R.drawable.bg_status_green)
            } else if (data.paymentStatus == "Đã huỷ") {
                statusRealtime.setBackgroundResource(R.drawable.bg_status_red)
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

}
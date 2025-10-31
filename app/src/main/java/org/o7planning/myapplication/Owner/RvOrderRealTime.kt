package org.o7planning.myapplication.Owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.ItemOderRealTimeBinding

interface OnOrderConfirmListener {
    fun onConfirmClick(data: dataTableManagement)
    fun onRefuseRealtimeClick(id: String)
}

class RvOrderRealTime(
    val list: List<dataTableManagement>,
    private val listener: OnOrderConfirmListener
) : RecyclerView.Adapter<RvOrderRealTime.viewHolderItem>() {


    inner class viewHolderItem(val binding: ItemOderRealTimeBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RvOrderRealTime.viewHolderItem {
        val binding =
            ItemOderRealTimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(holder: RvOrderRealTime.viewHolderItem, position: Int) {
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
            btnConfirmRealtime.setOnClickListener {
                listener.onConfirmClick(data)
            }
            btnRefuseRealtime.setOnClickListener {
                listener.onRefuseRealtimeClick(data.id.toString())
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }


}

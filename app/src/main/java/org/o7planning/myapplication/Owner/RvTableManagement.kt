package org.o7planning.myapplication.Owner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.ItemTableManagementBinding

interface setOnclickTableManagement{
    fun onClickComplete(data: dataTableManagement)
}


class RvTableManagement(val list: List<dataTableManagement>,
    private val listener: setOnclickTableManagement): RecyclerView.Adapter<RvTableManagement.viewHolderItem>()  {

    inner class viewHolderItem(val binding: ItemTableManagementBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): viewHolderItem {
        val binding = ItemTableManagementBinding.inflate(LayoutInflater.from(parent.context), parent,false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(
        holder: viewHolderItem,
        position: Int
    ) {
        holder.binding.apply {
            val data = list[position]
            if (data.phoneNumber == "null"){
                nameRealtime.text = "${data.name} - ${data.email}"
            }else{
                nameRealtime.text = "${data.name} - ${data.phoneNumber}"
            }
            timeRealtime.text = "Thời gian: ${data.startTime} - ${data.endTime}"
            address.text = data.addressClb
            personRealtime.text = data.person + " Người"
            manyRealtime.text = data.money.toString()
            statusTableManagement.text = data.status
            if (data.status == "Đã hoàn thành" || data.status == "Đã huỷ" || data.status == "Đã hoàn thành(owner)"){
                btnStatus.visibility = View.GONE
            }else{
                btnStatus.visibility = View.VISIBLE
            }
            btnStatus.setOnClickListener {
                listener.onClickComplete(data)
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

}
package org.o7planning.myapplication.Owner

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.ItemTableManagementBinding

interface setOnclickTableManagement {
    fun onClickComplete(data: dataTableManagement)
    fun onShowDetailsClick(data: dataTableManagement)
    fun onEditClick(data: dataTableManagement)
    fun onDeleteClick(data: dataTableManagement)
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
            val context = holder.binding.root.context
            val YELLOW_COLOR = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.yellow))
            val DEFAULT_COLOR = ColorStateList.valueOf(Color.WHITE)

            if (data.phoneNumber == "null"){
                tvCustomerName.text = "${data.name} - ${data.email}"
            }else{
                tvCustomerName.text = "${data.name} - ${data.phoneNumber}"
            }
            tvTimeAndLocation.text = "Thời gian: ${data.startTime} - ${data.endTime} - ${data.addressClb}"
            if (data.status == "Đang chơi"){
                btnComplete.visibility = View.VISIBLE
                val redColorStateList = ColorStateList.valueOf(Color.GREEN)
                tvStatusBadge.backgroundTintList = redColorStateList
            }else {
                btnComplete.visibility = View.GONE
            }

            if (data.status == "Đang chờ"){
                tvStatusBadge.backgroundTintList = YELLOW_COLOR
            }

            if (data.paymentStatus == "Chưa thanh toán" && (data.status == "Đã hoàn thành" || data.status == "Đã hoàn thành(manager)")){
                holder.binding.root.backgroundTintList = YELLOW_COLOR
            }else{
                holder.binding.root.backgroundTintList = DEFAULT_COLOR
            }

            btnComplete.setOnClickListener {
                listener.onClickComplete(data)
            }
            btnDetail.setOnClickListener {
                listener.onShowDetailsClick(data)
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

}
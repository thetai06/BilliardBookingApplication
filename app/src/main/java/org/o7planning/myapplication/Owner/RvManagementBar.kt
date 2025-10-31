package org.o7planning.myapplication.Owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.databinding.ItemBilliardsBarManagementBinding

interface setOnclickManagemenrBar{
    fun onClickEditBar(data: dataStore)
    fun onClickDeleteBar(data: dataStore)
}


class RvManagementBar(val list: List<dataStore>,
    private val listener: setOnclickManagemenrBar): RecyclerView.Adapter<RvManagementBar.viewHolderItem>() {

    inner class viewHolderItem(val binding: ItemBilliardsBarManagementBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): viewHolderItem {
        val binding = ItemBilliardsBarManagementBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(
        holder: viewHolderItem,
        position: Int
    ) {
        val store = list[position]

        holder.binding.apply {
            Glide.with(holder.itemView.context)
                .load(store.imageURL)
                .placeholder(org.o7planning.myapplication.R.drawable.quan_bi_a1)
                .error(org.o7planning.myapplication.R.drawable.quan_bi_a2)
                .into(ivManagement)
            nameBar.text = store.name
            locationBar.text = "Cơ sở: ${store.address}"
            phoneBar.text = "Số Điện Thoại: ${store.phone}"
            emailBar.text = "Email: ${store.email}"
            timeBar.text = "Thời gian hoạt động: ${store.openingHour} - ${store.closingHour}"
            tableBar.text = "Số lượng bàn: ${store.tableNumber}"
            desBar.text = "Tổng quan: ${store.des}"
            btnEdit.setOnClickListener {
                listener.onClickEditBar(store)
            }
            btnDelete.setOnClickListener {
                listener.onClickDeleteBar(store)
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }


}
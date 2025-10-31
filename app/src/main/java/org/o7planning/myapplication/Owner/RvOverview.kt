package org.o7planning.myapplication.Owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataStoreDisplayInfo
import org.o7planning.myapplication.databinding.ItemOverviewBinding

class RvOverview(val list: List<dataStoreDisplayInfo>) :
    RecyclerView.Adapter<RvOverview.viewHolderItem>() {
    inner class viewHolderItem(val binding: ItemOverviewBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): viewHolderItem {
        val binding =
            ItemOverviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return viewHolderItem(binding)
    }

    override fun onBindViewHolder(
        holder: viewHolderItem,
        position: Int
    ) {
        holder.binding.apply {
            val data = list[position]
            imgHeader.setImageResource(R.drawable.quan_bi_a2)
            txtTitleShop.text = data.name
            txtLocationOverview.text = data.address
            txtSumTable.text = data.totalBooking.toString()
            txtConfirm.text = data.simpleEnding.toString()
            txtProfit.text = data.profit.toString()
            txtProcessing.text = data.paidBookings.toString()
            txtStatisticsStatus.text = data.tableNumber
            txtTableActive.text = data.tableActive.toString()
            txtTableEmpty.text = data.tableEmpty.toString()
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

}
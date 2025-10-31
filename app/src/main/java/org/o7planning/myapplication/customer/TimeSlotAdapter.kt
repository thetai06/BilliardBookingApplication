package org.o7planning.myapplication.customer

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.o7planning.myapplication.R
import org.o7planning.myapplication.admin.TimeSlot
import java.text.SimpleDateFormat
import java.util.Locale

class TimeSlotAdapter(
    private val context: Context,
    private var timeSlots: List<TimeSlot>,
    private val onItemClick: (TimeSlot) -> Unit
) : RecyclerView.Adapter<TimeSlotAdapter.TimeSlotViewHolder>() {

    // Fragment sẽ cập nhật các giá trị này
    private var selectedStartTime: String? = null
    private var selectedEndTime: String? = null

    inner class TimeSlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tv_time_slot)
        val tvAvailable: TextView = itemView.findViewById(R.id.tv_available_tables)
        val cardView: CardView = itemView.findViewById(R.id.card_time_slot)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(timeSlots[position]) // Báo cho Fragment biết slot nào được click
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeSlotViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_time_slot, parent, false)
        return TimeSlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeSlotViewHolder, position: Int) {
        val slot = timeSlots[position]
        holder.tvTime.text = slot.time

        // Xác định trạng thái của slot này
        val isStartTime = slot.time == selectedStartTime
        val isEndTime = slot.time == selectedEndTime
        val isInRange = isSlotBetween(slot.time, selectedStartTime, selectedEndTime)

        when {
            // 1. HẾT BÀN (Ưu tiên cao nhất)
            slot.available_tables <= 0 -> {
                holder.tvAvailable.text = "Hết bàn"
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorRed))
                holder.tvTime.setTextColor(Color.GRAY)
                holder.tvAvailable.setTextColor(Color.GRAY)
            }
            // 2. LÀ GIỜ BẮT ĐẦU hoặc KẾT THÚC
            isStartTime || isEndTime -> {
                holder.tvAvailable.text = "${slot.available_tables} bàn"
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.green)) // Xanh đậm
                holder.tvTime.setTextColor(Color.WHITE)
                holder.tvAvailable.setTextColor(Color.WHITE)
            }
            // 3. NẰM TRONG KHOẢNG ĐÃ CHỌN (nhưng không phải đầu/cuối)
            isInRange -> {
                holder.tvAvailable.text = "${slot.available_tables} bàn"
                // Màu xanh hơi đậm hơn màu mặc định chút
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorViolet))
                holder.tvTime.setTextColor(Color.WHITE) // Chữ trắng để dễ đọc
                holder.tvAvailable.setTextColor(Color.WHITE)
            }
            // 4. CÒN BÀN & KHÔNG ĐƯỢC CHỌN
            else -> {
                holder.tvAvailable.text = "${slot.available_tables} bàn"
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorMain)) // Xanh nhạt
                holder.tvTime.setTextColor(Color.BLACK)
                holder.tvAvailable.setTextColor(Color.BLACK)
            }
        }
    }

    override fun getItemCount(): Int = timeSlots.size

    // Hàm kiểm tra xem một slot có nằm giữa start và end không
    private fun isSlotBetween(slotTimeStr: String, startTimeStr: String?, endTimeStr: String?): Boolean {
        if (startTimeStr == null || endTimeStr == null || slotTimeStr == startTimeStr || slotTimeStr == endTimeStr) {
            return false
        }
        return try {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            val slot = format.parse(slotTimeStr)!!
            val start = format.parse(startTimeStr)!!
            val end = format.parse(endTimeStr)!!
            slot.after(start) && slot.before(end)
        } catch (e: Exception) {
            false
        }
    }

    // Hàm để Fragment cập nhật dữ liệu timeline
    fun updateData(newTimeSlots: List<TimeSlot>) {
        timeSlots = newTimeSlots
        selectedStartTime = null
        selectedEndTime = null
        notifyDataSetChanged()
    }

    // Hàm để Fragment cập nhật giờ bắt đầu/kết thúc được chọn
    fun setSelectionRange(start: String?, end: String?) {
        selectedStartTime = start
        selectedEndTime = end
        notifyDataSetChanged() // Vẽ lại toàn bộ timeline
    }
}
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

    // Định dạng thời gian dùng chung
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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

        // 1. Xác định trạng thái của slot này
        val isStartTime = slot.time == selectedStartTime
        val isEndTime = slot.time == selectedEndTime

        // Kiểm tra xem slot có nằm giữa (hoặc trùng) với Start/End hay không.
        // Dùng hàm này để xác định slot đang được chọn.
        val isSlotSelected = isStartTime || isEndTime || isSlotBetween(slot.time, selectedStartTime, selectedEndTime)

        when {
            // 1. HẾT BÀN (Ưu tiên cao nhất)
            slot.available_tables <= 0 -> {
                holder.tvAvailable.text = "Hết bàn"
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorRed))
                holder.tvTime.setTextColor(Color.WHITE) // Đổi sang trắng để dễ đọc trên nền đỏ
                holder.tvAvailable.setTextColor(Color.WHITE)
            }
            // 2. NẰM TRONG KHOẢNG ĐÃ CHỌN (Bao gồm Start, End, và ở giữa)
            isSlotSelected -> {
                holder.tvAvailable.text = "${slot.available_tables} bàn"

                // Tô màu khác biệt cho Start/End để người dùng dễ nhận biết (ví dụ: xanh đậm hơn)
                if (isStartTime || isEndTime) {
                    holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.green)) // Xanh đậm cho điểm neo
                } else {
                    holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorViolet)) // Màu xanh/tím cho vùng giữa
                }

                holder.tvTime.setTextColor(Color.WHITE)
                holder.tvAvailable.setTextColor(Color.WHITE)
            }
            // 3. CÒN BÀN & KHÔNG ĐƯỢC CHỌN (Mặc định)
            else -> {
                holder.tvAvailable.text = "${slot.available_tables} bàn"
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorMain)) // Xanh nhạt
                holder.tvTime.setTextColor(Color.BLACK)
                holder.tvAvailable.setTextColor(Color.BLACK)
            }
        }
    }

    override fun getItemCount(): Int = timeSlots.size

    /**
     * SỬA LOGIC: Kiểm tra xem một slot có nằm GIỮA (không bao gồm điểm đầu và cuối)
     * hai thời điểm đã chọn hay không.
     */
    private fun isSlotBetween(slotTimeStr: String, startTimeStr: String?, endTimeStr: String?): Boolean {
        // Chỉ cần kiểm tra Start/End tồn tại
        if (startTimeStr == null || endTimeStr == null) {
            return false
        }

        // Trường hợp người dùng chọn Start và End giống nhau (chưa hoàn thành chọn 2 điểm)
        if (startTimeStr == endTimeStr) {
            return false
        }

        return try {
            val slot = timeFormat.parse(slotTimeStr)!!
            val start = timeFormat.parse(startTimeStr)!!
            val end = timeFormat.parse(endTimeStr)!!

            // So sánh slot phải nằm sau start và trước end
            slot.after(start) && slot.before(end)
        } catch (e: Exception) {
            // Log lỗi parsing nếu cần
            false
        }
    }

    // Hàm để Fragment cập nhật dữ liệu timeline
    fun updateData(newTimeSlots: List<TimeSlot>) {
        timeSlots = newTimeSlots
        selectedStartTime = null // Reset vùng chọn khi dữ liệu mới được tải
        selectedEndTime = null
        notifyDataSetChanged()
    }

    // Hàm để Fragment cập nhật giờ bắt đầu/kết thúc được chọn
    fun setSelectionRange(start: String?, end: String?) {
        // Tối ưu: Nếu không có gì thay đổi, không cần gọi notifyDataSetChanged()
        if (selectedStartTime == start && selectedEndTime == end) return

        selectedStartTime = start
        selectedEndTime = end
        notifyDataSetChanged() // Vẽ lại toàn bộ timeline
    }
}
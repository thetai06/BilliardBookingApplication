package org.o7planning.myapplication.Owner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import org.o7planning.myapplication.R
import org.o7planning.myapplication.admin.BusyInterval
import org.o7planning.myapplication.admin.DetailedTimelineData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class TimelineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Paint Styles ---
    private val busyPaint = Paint().apply { isAntiAlias = true } // Màu sẽ thay đổi (Đỏ, Vàng, Xám)
    private val linePaint = Paint().apply { color = Color.GRAY; strokeWidth = 2f }
    private val textPaint =
        Paint().apply { color = Color.BLACK; textSize = 22f; isAntiAlias = true } // Nhãn giờ

    private val selectionPaint = Paint().apply {
        color = Color.parseColor("#880099FF") // Màu xanh nhạt (semi-transparent)
    }

    // --- Biến Dữ liệu & Trạng thái ---
    private var selectionStartX: Float? = null
    private var selectionEndX: Float? = null
    private var openingHour: Int = 8
    private var closingHour: Int = 24
    private var totalTables: Int = 1
    private var rawBookings: List<BusyInterval> = emptyList()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // --- Hằng số Cấu hình ---
    private val CUSTOMER_MODE = 1
    private val OWNER_MODE = 2
    private var displayMode: Int = OWNER_MODE

    private val STATUS_BAR_HEIGHT = 50f
    private val SLOT_MINUTES = 5 // Đơn vị tính toán và vẽ (5 phút/slot)
    private val PIXELS_PER_MINUTE = 4f
    private val PIXELS_PER_SLOT = PIXELS_PER_MINUTE * SLOT_MINUTES

    private val totalMinutes: Int
        get() = max(0, (closingHour - openingHour) * 60)

    // =========================================================
    // KHỞI TẠO & CẬP NHẬT DỮ LIỆU
    // =========================================================

    /**
     * Mục đích: Cập nhật dữ liệu chi tiết từ server, đặt lại giờ hoạt động và tổng số bàn.
     */
    fun updateDetailedTimeline(data: DetailedTimelineData) {
        rawBookings = data.busyBookings
        totalTables = max(1, data.totalTables)

        openingHour = data.openingHour.split(":")[0].toIntOrNull() ?: 8
        closingHour = data.closingHour.split(":")[0].toIntOrNull() ?: 24

        // Chuẩn hóa giờ
        if (closingHour == 0) closingHour = 24
        if (openingHour >= closingHour) {
            openingHour = 0
            closingHour = 24
        }
        clearSelection() // Xóa vùng chọn cũ khi dữ liệu mới được tải
        requestLayout() // Yêu cầu tính toán lại kích thước (onMeasure)
        invalidate() // Vẽ lại
    }

    /**
     * Mục đích: Thiết lập chế độ hiển thị (Owner 3 màu hay Customer Xanh/Đỏ).
     */
    fun setCustomerMode(isCustomer: Boolean) {
        displayMode = if (isCustomer) CUSTOMER_MODE else OWNER_MODE
        invalidate()
    }

    // =========================================================
    // XỬ LÝ TƯƠNG TÁC CHỌN VÙNG (HIGHLIGHT) - Giữ nguyên
    // =========================================================

    /**
     * Mục đích: Thiết lập vùng chọn giờ (highlight) dựa trên tọa độ pixel.
     */
    fun setSelectionRange(startX: Float, endX: Float) {
        selectionStartX = min(startX, endX)
        selectionEndX = max(startX, endX)
        invalidate() // Vẽ lại để hiển thị highlight
    }

    /**
     * Mục đích: Xóa vùng chọn (highlight).
     */
    fun clearSelection() {
        selectionStartX = null
        selectionEndX = null
        invalidate() // Vẽ lại
    }

    /**
     * Mục đích: Kiểm tra xem khoảng pixel được chọn [startX, endX] có bất kỳ slot nào
     * bị full (màu ĐỎ) hay đã trôi qua (màu XÁM) hay không.
     */
    fun isPixelRangeBusy(startX: Float, endX: Float): Boolean {
        if (rawBookings.isEmpty()) return false

        val finalStartX = min(startX, endX)
        val finalEndX = max(startX, endX)

        // Tính toán các slot cần kiểm tra (chuyển đổi pixel về phút)
        val startMinute = (finalStartX / PIXELS_PER_MINUTE).toInt()
        val endMinute = (finalEndX / PIXELS_PER_MINUTE).toInt()

        val currentCalendar = Calendar.getInstance()
        val todayStr =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(currentCalendar.time)

        // Lấy ngày đang xem (từ tag)
        val chartCalendar = this.tag as? Calendar ?: currentCalendar
        val chartDateStr =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(chartCalendar.time)


        val chartStartMoment = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, openingHour); set(Calendar.MINUTE, 0); set(
            Calendar.SECOND,
            0
        ); set(Calendar.MILLISECOND, 0)
        }.time

        var currentCheckMinute = startMinute
        while (currentCheckMinute < endMinute) {

            val slotStartCal = Calendar.getInstance().apply { time = chartStartMoment }
            slotStartCal.add(Calendar.MINUTE, currentCheckMinute)
            val slotEndCal = Calendar.getInstance().apply { time = chartStartMoment }
            slotEndCal.add(Calendar.MINUTE, currentCheckMinute + SLOT_MINUTES)

            // 🌟 SỬA: Đặt slot về ngày đang xem để so sánh chính xác
            slotStartCal.set(
                chartCalendar.get(Calendar.YEAR),
                chartCalendar.get(Calendar.MONTH),
                chartCalendar.get(Calendar.DAY_OF_MONTH)
            )
            slotEndCal.set(
                chartCalendar.get(Calendar.YEAR),
                chartCalendar.get(Calendar.MONTH),
                chartCalendar.get(Calendar.DAY_OF_MONTH)
            )


            var busyCount = 0
            var isPast = false // Biến cục bộ để kiểm tra trong vòng lặp này

            // 1. Kiểm tra PAST TIME
            val todayDateOnly = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
                Calendar.MILLISECOND,
                0
            )
            }
            val chartDateOnly = chartCalendar.clone() as Calendar
            chartDateOnly.set(Calendar.HOUR_OF_DAY, 0); chartDateOnly.set(
                Calendar.MINUTE,
                0
            ); chartDateOnly.set(Calendar.SECOND, 0); chartDateOnly.set(Calendar.MILLISECOND, 0)

            if (chartDateOnly.before(todayDateOnly)) {
                // Trường hợp 1: Ngày trong quá khứ -> TOÀN BỘ XÁM
                isPast = true
            } else if (chartDateStr == todayStr) {
                // Trường hợp 2: Ngày hôm nay -> Kiểm tra giờ đã qua
                if (slotEndCal.time.before(currentCalendar.time)) {
                    isPast = true
                }
            }

            // 2. Đếm số bàn bận trong Slot này
            if (!isPast) {
                for (booking in rawBookings) {
                    try {
                        val bookingStartCal = Calendar.getInstance()
                            .apply { time = timeFormat.parse(booking.startTime)!! }
                        val bookingEndCal = Calendar.getInstance()
                            .apply { time = timeFormat.parse(booking.endTime)!! }

                        // Giả định booking cùng ngày với chartDate
                        bookingStartCal.set(
                            chartCalendar.get(Calendar.YEAR),
                            chartCalendar.get(Calendar.MONTH),
                            chartCalendar.get(Calendar.DAY_OF_MONTH)
                        )
                        bookingEndCal.set(
                            chartCalendar.get(Calendar.YEAR),
                            chartCalendar.get(Calendar.MONTH),
                            chartCalendar.get(Calendar.DAY_OF_MONTH)
                        )

                        if (bookingEndCal.before(bookingStartCal)) {
                            bookingEndCal.add(Calendar.DAY_OF_MONTH, 1)
                        }

                        // Kiểm tra Overlap: SlotStart < BookingEnd AND SlotEnd > BookingStart
                        if (slotStartCal.time.before(bookingEndCal.time) && slotEndCal.time.after(
                                bookingStartCal.time
                            )
                        ) {
                            busyCount++
                        }
                    } catch (e: Exception) { /* Bỏ qua lỗi parse */
                    }
                }
            }

            // 3. Kiểm tra FULL BÀN (MÀU ĐỎ)
            if (isPast || busyCount >= totalTables) {
                return true // Bận: Full bàn hoặc đã trôi qua
            }

            currentCheckMinute += SLOT_MINUTES
        }

        return false // Khoảng chọn hợp lệ
    }

    // =========================================================
    // VẼ (DRAWING)
    // =========================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalMinutes <= 0) return

        val totalChartWidth = totalMinutes * PIXELS_PER_MINUTE
        val leftOffset = paddingStart.toFloat()
        val viewHeight = height.toFloat()

        val currentCalendar = Calendar.getInstance()
        val todayStr =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(currentCalendar.time)

        // 🌟 Lấy ngày đang xem (từ tag)
        val chartCalendar = this.tag as? Calendar ?: currentCalendar
        val chartDateStr =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(chartCalendar.time)


        // Lấy thời điểm mở cửa làm mốc 0
        val chartStartMoment = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, openingHour); set(Calendar.MINUTE, 0); set(
            Calendar.SECOND,
            0
        ); set(Calendar.MILLISECOND, 0)
        }.time

        // 🌟 Lấy ngày hiện tại chỉ để so sánh ngày
        val todayDateOnly = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
            Calendar.MILLISECOND,
            0
        )
        }
        val chartDateOnly = chartCalendar.clone() as Calendar
        chartDateOnly.set(Calendar.HOUR_OF_DAY, 0); chartDateOnly.set(
            Calendar.MINUTE,
            0
        ); chartDateOnly.set(Calendar.SECOND, 0); chartDateOnly.set(Calendar.MILLISECOND, 0)


        // Vòng lặp vẽ từng SLOT 5 phút
        var currentMinute = 0
        val totalSlots = totalMinutes / SLOT_MINUTES

        for (i in 0 until totalSlots) {
            val slotStartMinute = currentMinute

            // Vị trí X của slot hiện tại
            val startX = leftOffset + slotStartMinute * PIXELS_PER_MINUTE
            val endX = leftOffset + (slotStartMinute + SLOT_MINUTES) * PIXELS_PER_MINUTE

            var busyCount = 0
            var isPast = false

            // 1. Xác định thời điểm hiện tại của slot
            val slotStartCal = Calendar.getInstance().apply { time = chartStartMoment }
            slotStartCal.add(Calendar.MINUTE, slotStartMinute)
            val slotEndCal = Calendar.getInstance().apply { time = chartStartMoment }
            slotEndCal.add(Calendar.MINUTE, slotStartMinute + SLOT_MINUTES)

            // 🌟 Đặt slot về ngày đang xem để so sánh chính xác
            slotStartCal.set(
                chartCalendar.get(Calendar.YEAR),
                chartCalendar.get(Calendar.MONTH),
                chartCalendar.get(Calendar.DAY_OF_MONTH)
            )
            slotEndCal.set(
                chartCalendar.get(Calendar.YEAR),
                chartCalendar.get(Calendar.MONTH),
                chartCalendar.get(Calendar.DAY_OF_MONTH)
            )

            // 2. KIỂM TRA PAST TIME
            if (chartDateOnly.before(todayDateOnly)) {
                // Trường hợp 1: Ngày trong quá khứ -> TOÀN BỘ XÁM
                isPast = true
            } else if (chartDateStr == todayStr) {
                // Trường hợp 2: Ngày hôm nay -> Kiểm tra giờ đã qua
                if (slotEndCal.time.before(currentCalendar.time)) {
                    isPast = true
                }
            }

            // 3. Đếm số bàn bận trong Slot này
            if (!isPast) {
                for (booking in rawBookings) {
                    try {
                        val bookingStartCal = Calendar.getInstance()
                            .apply { time = timeFormat.parse(booking.startTime)!! }
                        val bookingEndCal = Calendar.getInstance()
                            .apply { time = timeFormat.parse(booking.endTime)!! }

                        // Đặt booking về ngày đang xem
                        bookingStartCal.set(
                            chartCalendar.get(Calendar.YEAR),
                            chartCalendar.get(Calendar.MONTH),
                            chartCalendar.get(Calendar.DAY_OF_MONTH)
                        )
                        bookingEndCal.set(
                            chartCalendar.get(Calendar.YEAR),
                            chartCalendar.get(Calendar.MONTH),
                            chartCalendar.get(Calendar.DAY_OF_MONTH)
                        )

                        if (bookingEndCal.before(bookingStartCal)) {
                            bookingEndCal.add(Calendar.DAY_OF_MONTH, 1)
                        }

                        if (slotStartCal.time.before(bookingEndCal.time) && slotEndCal.time.after(
                                bookingStartCal.time
                            )
                        ) {
                            busyCount++
                        }
                    } catch (e: Exception) { /* Bỏ qua lỗi parse */
                    }
                }
            }

            // 4. XÁC ĐỊNH MÀU
            val color = when {
                isPast -> Color.GRAY // Xám: Đã trôi qua (Không thể chọn)
                displayMode == CUSTOMER_MODE -> {
                    // CHẾ ĐỘ CUSTOMER (Xanh/Đỏ)
                    if (busyCount >= totalTables) Color.RED // Đỏ: Full bàn (Không thể chọn)
                    else ContextCompat.getColor(
                        context,
                        R.color.green
                    ) // Xanh: Còn trống (Có thể chọn)
                }

                else -> {
                    // CHẾ ĐỘ OWNER (Xanh/Vàng/Đỏ)
                    when {
                        busyCount >= totalTables -> Color.RED
                        busyCount > 0 -> Color.YELLOW
                        else -> ContextCompat.getColor(context, R.color.green) // Xanh: Trống
                    }
                }
            }

            // 5. VẼ CỘT (SLOT)
            busyPaint.color = color
            canvas.drawRect(startX, 0f, endX, STATUS_BAR_HEIGHT, busyPaint)

            currentMinute += SLOT_MINUTES
        }

        // 6. VẼ VÙNG CHỌN (HIGHLIGHT) - Giữ nguyên
        selectionStartX?.let { start ->
            selectionEndX?.let { end ->
                canvas.drawRect(start, 0f, end, STATUS_BAR_HEIGHT, selectionPaint)
            }
        }

        // 7. VẼ CÁC ĐƯỜNG KẺ DỌC VÀ NHÃN GIỜ (AXIS SÁT ĐÁY) - Giữ nguyên
        val axisYPosition = viewHeight - 5f

        for (h in openingHour..closingHour) {
            val hour = h % 24
            val hourTotalMinutes = (h - openingHour) * 60

            val xPos = leftOffset + hourTotalMinutes * PIXELS_PER_MINUTE

            // Vẽ đường kẻ dọc (Chỉ kéo dài qua thanh trạng thái)
            canvas.drawLine(xPos, 0f, xPos, STATUS_BAR_HEIGHT, linePaint)

            // Vẽ nhãn giờ (Đặt sát đáy)
            val hourStr = String.format("%02d:00", hour)

            canvas.drawText(
                hourStr,
                xPos - (textPaint.measureText(hourStr) / 2),
                axisYPosition,
                textPaint
            )
        }
    }
}
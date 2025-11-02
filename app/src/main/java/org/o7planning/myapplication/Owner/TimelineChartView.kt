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
import kotlin.math.roundToInt

class TimelineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Paint Styles ---
    private val busyPaint = Paint().apply { isAntiAlias = true }
    private val linePaint = Paint().apply { color = Color.GRAY; strokeWidth = 2f }
    private val textPaint =
        Paint().apply { color = Color.BLACK; textSize = 22f; isAntiAlias = true }

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
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) // Dùng chung cho ngày

    // --- Hằng số Cấu hình ---
    private val CUSTOMER_MODE = 1
    private val OWNER_MODE = 2
    private var displayMode: Int = OWNER_MODE

    private val STATUS_BAR_HEIGHT = 50f
    private val SLOT_MINUTES = 5
    private val PIXELS_PER_MINUTE = 4f
    private val PIXELS_PER_SLOT = PIXELS_PER_MINUTE * SLOT_MINUTES

    // TỔNG SỐ PHÚT HOẠT ĐỘNG
    private val totalMinutes: Int
        get() = max(0, (closingHour - openingHour) * 60)

    // =========================================================
    // HÀM TÍNH GRADIENT MÀU (MỚI)
    // =========================================================

    /**
     * Mục đích: Trả về màu nội suy (interpolation) dựa trên tỷ lệ bận rộn.
     * Xanh (0%) -> Vàng (50%) -> Đỏ (100%)
     */
    private fun getGradientColor(busyCount: Int, totalTables: Int): Int {
        if (totalTables <= 0) return Color.GREEN // Fallback an toàn

        val ratio = busyCount.toFloat() / totalTables.toFloat()

        // Define the color steps in ARGB
        val GREEN = 0xFF00FF00.toInt() // Màu Xanh Lá
        val YELLOW = 0xFFFFFF00.toInt() // Màu Vàng
        val RED = 0xFFFF0000.toInt() // Màu Đỏ

        if (ratio <= 0f) return GREEN
        if (ratio >= 1f) return RED

        // Hàm nội suy giữa hai màu (ARGB interpolation)
        fun interpolate(colorStart: Int, colorEnd: Int, ratio: Float): Int {
            val r = ((Color.red(colorEnd) - Color.red(colorStart)) * ratio).roundToInt() + Color.red(colorStart)
            val g = ((Color.green(colorEnd) - Color.green(colorStart)) * ratio).roundToInt() + Color.green(colorStart)
            val b = ((Color.blue(colorEnd) - Color.blue(colorStart)) * ratio).roundToInt() + Color.blue(colorStart)
            return Color.rgb(r, g, b)
        }

        return if (ratio <= 0.5f) {
            // Nửa đầu: Green (0.0) -> Yellow (0.5)
            val localRatio = ratio * 2f
            interpolate(GREEN, YELLOW, localRatio)
        } else {
            // Nửa sau: Yellow (0.5) -> Red (1.0)
            val localRatio = (ratio - 0.5f) * 2f
            interpolate(YELLOW, RED, localRatio)
        }
    }


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

        // Chuẩn hóa giờ đóng cửa 00:xx thành 24:00 nếu nó không phải là 00:00 thực sự (qua ngày mới)
        if (closingHour == 0 && data.closingHour != "00:00") closingHour = 24

        // Đảm bảo giờ mở cửa nhỏ hơn giờ đóng cửa
        if (openingHour >= closingHour) {
            Log.w("TimelineChart", "Invalid opening/closing hour. Resetting to 0-24.")
            openingHour = 0
            closingHour = 24
        }

        clearSelection()
        requestLayout() // Yêu cầu tính toán lại kích thước (onMeasure) - CỰC KỲ QUAN TRỌNG
        invalidate()
    }

    /**
     * Mục đích: Thiết lập chế độ hiển thị (Owner 3 màu hay Customer Xanh/Đỏ).
     */
    fun setCustomerMode(isCustomer: Boolean) {
        displayMode = if (isCustomer) CUSTOMER_MODE else OWNER_MODE
        invalidate()
    }

    // =========================================================
    // XỬ LÝ KÍCH THƯỚC (QUAN TRỌNG NHẤT CHO HORIZONTAL SCROLL)
    // =========================================================

    /**
     * SỬA LỖI: Báo cho HorizontalScrollView biết chiều rộng cần thiết để View không bị cắt.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalChartWidth = (totalMinutes * PIXELS_PER_MINUTE + paddingStart + paddingEnd).toInt()

        // Chiều rộng mong muốn, đảm bảo View có thể cuộn hết timeline
        val desiredWidth = max(totalChartWidth, suggestedMinimumWidth)

        // Chiều cao: Thanh trạng thái + khoảng cách + nhãn giờ
        val desiredHeight = (STATUS_BAR_HEIGHT + textPaint.textSize + 20f + paddingTop + paddingBottom).toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
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
        invalidate()
    }

    /**
     * Mục đích: Xóa vùng chọn (highlight).
     */
    fun clearSelection() {
        selectionStartX = null
        selectionEndX = null
        invalidate()
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
        val todayStr = dateFormat.format(currentCalendar.time)

        // Lấy ngày đang xem (từ tag)
        val chartCalendar = this.tag as? Calendar ?: currentCalendar
        val chartDateStr = dateFormat.format(chartCalendar.time)


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

            // Đặt slot về ngày đang xem để so sánh chính xác
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
            var isPast = false

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

            // 🌟 SỬA LOGIC: Kiểm tra Past Time
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

                        // Đã loại bỏ xử lý qua đêm (bookingEndCal.add(Calendar.DAY_OF_MONTH, 1))

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

        val leftOffset = paddingStart.toFloat()
        val viewHeight = height.toFloat()

        val currentCalendar = Calendar.getInstance()
        val todayStr = dateFormat.format(currentCalendar.time)

        val chartCalendar = this.tag as? Calendar ?: currentCalendar
        val chartDateStr = dateFormat.format(chartCalendar.time)


        // Lấy thời điểm mở cửa làm mốc 0
        val chartStartMoment = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, openingHour); set(Calendar.MINUTE, 0); set(
            Calendar.SECOND,
            0
        ); set(Calendar.MILLISECOND, 0)
        }.time

        // Lấy ngày hiện tại chỉ để so sánh ngày
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

            // Đặt slot về ngày đang xem để so sánh chính xác
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
            } else if (chartDateOnly.timeInMillis == todayDateOnly.timeInMillis) { // KIỂM TRA CHỈ KHI LÀ NGÀY HÔM NAY (Sử dụng TimeInMillis)
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

                        // Đã loại bỏ xử lý qua đêm (bookingEndCal.add(Calendar.DAY_OF_MONTH, 1))

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
                    else getGradientColor(busyCount, totalTables) // Dùng gradient
                }

                else -> {
                    // CHẾ ĐỘ OWNER (Xanh/Vàng/Đỏ) - Dùng gradient
                    getGradientColor(busyCount, totalTables)
                }
            }

            // 5. VẼ CỘT (SLOT)
            busyPaint.color = color
            canvas.drawRect(startX, 0f, endX, STATUS_BAR_HEIGHT, busyPaint)

            currentMinute += SLOT_MINUTES
        }

        // 6. VẼ VÙNG CHỌN (HIGHLIGHT)
        selectionStartX?.let { start ->
            selectionEndX?.let { end ->
                canvas.drawRect(start, 0f, end, STATUS_BAR_HEIGHT, selectionPaint)
            }
        }

        // 7. VẼ CÁC ĐƯỜNG KẺ DỌC VÀ NHÃN GIỜ (AXIS SÁT ĐÁY) - Đã sửa vòng lặp
        val axisYPosition = viewHeight - 5f

        for (h in openingHour..closingHour) {
            val hour = h % 24
            val hourTotalMinutes = (h - openingHour) * 60

            val xPos = leftOffset + hourTotalMinutes * PIXELS_PER_MINUTE

            // Vẽ đường kẻ dọc
            canvas.drawLine(xPos, 0f, xPos, STATUS_BAR_HEIGHT, linePaint)

            // Chỉ vẽ nhãn giờ nếu nó không phải là giờ cuối (closingHour)
            if (h < closingHour || (h == closingHour && closingHour == 24)) {
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
}
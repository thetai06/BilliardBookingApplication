package org.o7planning.myapplication.Owner

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.R
import org.o7planning.myapplication.admin.AvailabilityRequest
import org.o7planning.myapplication.admin.AvailabilityResponse
import org.o7planning.myapplication.admin.DetailedTimelineData
import org.o7planning.myapplication.admin.TimelineRequest
import org.o7planning.myapplication.customer.RetrofitClient
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.FragmentTablemanagementBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.jvm.java

class FragmentTableManagement : Fragment(), setOnclickTableManagement {

    private lateinit var binding: FragmentTablemanagementBinding
    private lateinit var fullList: ArrayList<dataTableManagement>
    private lateinit var list: ArrayList<dataTableManagement>

    private lateinit var dbRefTableManagement: DatabaseReference
    private lateinit var dbRefUser: DatabaseReference
    private lateinit var dbRefStore: DatabaseReference

    private lateinit var tableManagementAdapter: RvTableManagement
    private lateinit var mAuth: FirebaseAuth

    private var ownerId: String? = null
    private var currentStoreId: String? = null
    private var storeOwnerId: String? = null
    private var address: String? = null
    private var totalTablesInStore: Int = 0 // Tổng số bàn

    private var currentStatusFilter: String? = null
    private var currentDateFilter: String? = null
    private var currentPaymentStatusFilter: String? = null
    private var openingHour: String? = null // Giờ mở cửa
    private var closingHour: String? = null // Giờ đóng cửa

    // --- Lifecycle Methods ---

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTablemanagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRefTableManagement = FirebaseDatabase.getInstance().getReference("dataBookTable")
        dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser")
        dbRefStore = FirebaseDatabase.getInstance().getReference("dataStore")

        fullList = arrayListOf()
        list = arrayListOf()

        mAuth = FirebaseAuth.getInstance()
        ownerId = mAuth.currentUser?.uid

        // Thiết lập ngày mặc định cho Timeline và Lọc
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        binding.tvTimelineDate.text = today
        // 🌟 SỬA: SET TAG LÀ CALENDAR OBJECT
        binding.timelineChartView.tag = Calendar.getInstance()
        binding.tvTimelineDate.setOnClickListener { showTimelineDatePickerDialog() }
        currentDateFilter = today // Thiết lập lọc đơn hàng mặc định là hôm nay

        boxTableManagement()
        fetchUserStoreId()
        setupFilterListeners()
        setupNewBookingListener()
    }

    // --- Data Fetching and Initialization ---

    private fun fetchUserStoreId() {
        ownerId?.let { uid ->
            dbRefUser.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentStoreId = snapshot.child("storeId").getValue(String::class.java)
                    if (currentStoreId != null) {
                        loadStoreInfo(currentStoreId!!)
                        dataBoxTableManagement()
                    } else {
                        Toast.makeText(requireContext(), "Tài khoản chưa được gán cơ sở.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Lỗi tải thông tin người dùng: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun loadStoreInfo(storeId: String) {
        dbRefStore.child(storeId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val storeData = snapshot.getValue(dataStore::class.java)
                    totalTablesInStore = storeData?.tableNumber?.toIntOrNull() ?: 0
                    address = storeData?.address
                    storeOwnerId = storeData?.ownerId

                    openingHour = storeData?.openingHour
                    closingHour = storeData?.closingHour

                    // Tải Timeline chi tiết khi có Store ID và Ngày
                    // Lấy Calendar object từ tag, nếu không có thì dùng Calendar.getInstance()
                    val chartCalendar = binding.timelineChartView.tag as? Calendar ?: Calendar.getInstance()
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(chartCalendar.time)

                    fetchDetailedTimelineData(storeId, date)

                    dataBoxTableManagement()

                } else {
                    Log.e("StoreInfo", "Store không tồn tại: $storeId")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("StoreInfo", "Lỗi tải store: ${error.message}")
            }
        })
    }

    private fun dataBoxTableManagement() {
        dbRefTableManagement.orderByChild("storeId").equalTo(currentStoreId)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    fullList.clear()
                    if (snapshot.exists()){
                        for (listSnap in snapshot.children){
                            val listData = listSnap.getValue(dataTableManagement::class.java)
                            listData?.let { fullList.add(it) }
                        }
                    }
                    filterData()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TableManagement", "Lỗi tải dữ liệu đặt bàn: ${error.message}")
                }
            })
    }

    private fun boxTableManagement() {
        tableManagementAdapter = RvTableManagement(list,this)
        binding.rvTableManagement.adapter = tableManagementAdapter
        binding.rvTableManagement.layoutManager = GridLayoutManager(
            requireContext(),
            1,
            GridLayoutManager.VERTICAL,
            false
        )
    }

    // --- Timeline and Booking Logic ---

    private fun fetchDetailedTimelineData(storeId: String, date: String) {
        Toast.makeText(context, "Đang tải Timeline chi tiết ngày $date...", Toast.LENGTH_SHORT).show()
        val request = TimelineRequest(storeId, date)

        // Lấy giờ hoạt động đã tải từ Firebase (Fallback)
        val defaultOpen = openingHour ?: "00:00"
        val defaultClose = closingHour ?: "23:59"

        RetrofitClient.apiService.getDetailedTimelineData(request).enqueue(object : Callback<DetailedTimelineData> {
            override fun onResponse(call: Call<DetailedTimelineData>, response: Response<DetailedTimelineData>) {
                if (response.isSuccessful && response.body() != null) {
                    response.body()?.let { data ->
                        binding.timelineChartView.updateDetailedTimeline(data)
                    }
                } else {
                    Log.e("TimelineAPI", "Server error fetching detailed timeline: ${response.code()}")
                    Toast.makeText(context, "Lỗi tải timeline từ server (${response.code()}).", Toast.LENGTH_SHORT).show()

                    // KHI LỖI: SỬ DỤNG GIỜ HOẠT ĐỘNG ĐÃ LƯU
                    binding.timelineChartView.updateDetailedTimeline(
                        DetailedTimelineData(totalTables = 1, openingHour = defaultOpen, closingHour = defaultClose, busyBookings = emptyList())
                    )
                }
            }
            override fun onFailure(call: Call<DetailedTimelineData>, t: Throwable) {
                Log.e("TimelineAPI", "Lỗi kết nối khi tải full timeline: ${t.message}")
                Toast.makeText(context, "Lỗi kết nối khi tải full timeline: ${t.message}", Toast.LENGTH_SHORT).show()

                // KHI LỖI: SỬ DỤNG GIỜ HOẠT ĐỘNG ĐÃ LƯU
                binding.timelineChartView.updateDetailedTimeline(
                    DetailedTimelineData(totalTables = 1, openingHour = defaultOpen, closingHour = defaultClose, busyBookings = emptyList())
                )
            }
        })
    }

    private fun setupNewBookingListener() {
        binding.btnNewBooking.setOnClickListener {
            if (currentStoreId != null) {
                showNewBookingDialog()
            } else {
                Toast.makeText(requireContext(), "Không thể đặt bàn: Thiếu ID cơ sở.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Hàm tạo booking mới
    private fun createNewBooking(
        name: String,
        phone: String,
        date: String,
        startTime: String,
        endTime: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val storeId = currentStoreId ?: return onFailure("Thiếu Store ID")
        val totalTablesToBook = 1

        val request = AvailabilityRequest(
            storeId = storeId,
            dateTime = date,
            startTime = startTime,
            endTime = endTime,
            totalTables = totalTablesInStore
        )

        RetrofitClient.apiService.checkAvailability(request).enqueue(object :
            Callback<AvailabilityResponse> {
            override fun onResponse(call: Call<AvailabilityResponse>, response: Response<AvailabilityResponse>) {
                if (response.isSuccessful) {
                    val checkResponse = response.body()

                    if (checkResponse?.available == true && (checkResponse.availableCount ?: 0) >= totalTablesToBook) {

                        val newBookingRef = dbRefTableManagement.push()
                        val newBookingId = newBookingRef.key ?: return onFailure("Không tạo được key Firebase")

                        val newBooking = dataTableManagement(
                            id = newBookingId,
                            userId = ownerId,
                            storeId = storeId,
                            storeOwnerId = storeOwnerId,
                            name = name,
                            phoneNumber = phone,
                            dateTime = date,
                            startTime = startTime,
                            endTime = endTime,
                            paymentStatus = "Thanh toán tại quầy",
                            addressClb = address,
                            money = 0.0,
                            person = "1 người",
                            status = "Đang chơi"
                        )

                        newBookingRef.setValue(newBooking)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onFailure(it.message ?: "Lỗi lưu đơn vào Firebase") }

                    } else {
                        onFailure(checkResponse?.message ?: "Hiện không còn bàn trống vào thời điểm này.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: response.message()
                    Log.e("CreateBooking", "Lỗi Server ${response.code()}: $errorBody")
                    onFailure("Lỗi Server: ${response.code()} - $errorBody")
                }
            }

            override fun onFailure(call: Call<AvailabilityResponse>, t: Throwable) {
                Log.e("CreateBooking", "Lỗi kết nối API: ${t.message}", t)
                onFailure("Lỗi kết nối API: ${t.message}")
            }
        })
    }

    // --- Filtering Logic ---

    private fun setupFilterListeners() {
        binding.btnFilterStatus.setOnClickListener { showStatusFilterDialog() }
        binding.btnFilterPaymentStatus.setOnClickListener { showPaymentStatusFilterDialog() }
        binding.btnClearFilters.setOnClickListener { clearFilters() }
    }

    private fun filterData() {
        var filtered = fullList.toList()

        if (currentStatusFilter != null) {
            filtered = filtered.filter { it.status == currentStatusFilter }
            binding.btnFilterStatus.text = currentStatusFilter
        } else {
            binding.btnFilterStatus.setText("Trạng thái ĐH")
        }

        if (currentPaymentStatusFilter != null) {
            filtered = filtered.filter { it.paymentStatus == currentPaymentStatusFilter }
            binding.btnFilterPaymentStatus.text = currentPaymentStatusFilter
        } else {
            binding.btnFilterPaymentStatus.setText("Trạng thái TT")
        }

        if (currentDateFilter != null) {
            filtered = filtered.filter { it.dateTime == currentDateFilter }
            binding.btnFilterDate.text = currentDateFilter // 🌟 VẪN CẬP NHẬT TEXT CỦA NÚT NGÀY
        } else {
            binding.btnFilterDate.setText("Ngày")
        }

        list.clear()
        list.addAll(filtered)
        tableManagementAdapter.notifyDataSetChanged()

        binding.tvNoData.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun clearFilters() {
        currentStatusFilter = null
        currentDateFilter = null
        currentPaymentStatusFilter = null
        filterData()

        // Cập nhật hiển thị ngày Timeline về hôm nay khi xóa lọc
        val todayCalendar = Calendar.getInstance()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(todayCalendar.time)
        binding.tvTimelineDate.text = today
        binding.timelineChartView.tag = todayCalendar // 🌟 SET TAG LÀ CALENDAR OBJECT
        currentStoreId?.let { fetchDetailedTimelineData(it, today) }

        Toast.makeText(requireContext(), "Đã xóa tất cả bộ lọc", Toast.LENGTH_SHORT).show()
    }

    // --- Dialogs (Input/Filter) ---

    private fun showTimelineDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, monthOfYear)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val selectedDate = sdf.format(calendar.time)

            binding.tvTimelineDate.text = selectedDate
            binding.timelineChartView.tag = calendar.clone() // 🌟 SET TAG LÀ CALENDAR OBJECT

            currentDateFilter = selectedDate
            filterData() // Kích hoạt lọc đơn hàng với ngày mới

            currentStoreId?.let {
                fetchDetailedTimelineData(it, selectedDate)
            }
        }

        val dialog = DatePickerDialog(
            requireContext(), dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // ❌ ĐÃ BỎ CHẶN NGÀY QUÁ KHỨ (Bằng cách xóa dòng minDate)
        dialog.show()
    }

    private fun showNewBookingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_booking_simple, null)

        val tvBookingDate = dialogView.findViewById<TextView>(R.id.tvBookingDate)
        val tvBookingStartTime = dialogView.findViewById<TextView>(R.id.tvBookingStartTime)
        val tvBookingEndTime = dialogView.findViewById<TextView>(R.id.tvBookingEndTime)
        val edtCustomerName = dialogView.findViewById<TextView>(R.id.edtCustomerName)
        val edtCustomerPhone = dialogView.findViewById<TextView>(R.id.edtCustomerPhone)
        val btnConfirmBooking = dialogView.findViewById<Button>(R.id.btnConfirmBooking)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val defaultClosingHour = closingHour ?: "00:00"

        val calendar = Calendar.getInstance()
        var selectedDate: String? = null
        var selectedStartTime: String? = null
        var selectedEndTime: String? = null

        tvBookingDate.setOnClickListener {
            showDatePicker(calendar) { date ->
                selectedDate = date
                tvBookingDate.text = date
            }
        }

        tvBookingStartTime.setOnClickListener {
            showTimePicker(calendar) { time ->
                selectedStartTime = time
                tvBookingStartTime.text = time
                selectedEndTime = defaultClosingHour
                tvBookingEndTime.text = selectedEndTime
            }
        }

        tvBookingEndTime.setOnClickListener {
            showTimePicker(calendar) { time ->
                selectedEndTime = time
                tvBookingEndTime.text = time
            }
        }

        btnConfirmBooking.setOnClickListener {
            val name = edtCustomerName.text.toString().trim()
            val phone = edtCustomerPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || selectedDate.isNullOrEmpty() || selectedStartTime.isNullOrEmpty() || selectedEndTime.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createNewBooking(
                name = name,
                phone = phone,
                date = selectedDate!!,
                startTime = selectedStartTime!!,
                endTime = selectedEndTime!!,
                onSuccess = {
                    alertDialog.dismiss()
                    Toast.makeText(requireContext(), "Đặt bàn thành công!", Toast.LENGTH_LONG).show()
                },
                onFailure = { message ->
                    Toast.makeText(requireContext(), "Lỗi đặt bàn: $message", Toast.LENGTH_LONG).show()
                    Log.e("Order", message, null)
                }
            )
        }

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()
    }

    private fun showDatePicker(calendar: Calendar, onDateSelected: (String) -> Unit) {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, monthOfYear)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            onDateSelected(sdf.format(calendar.time))
        }

        val dialog = DatePickerDialog(
            requireContext(), dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // ❌ ĐÃ BỎ CHẶN NGÀY QUÁ KHỨ (Bằng cách xóa dòng minDate)
        dialog.show()
    }

    private fun showTimePicker(calendar: Calendar, onTimeSelected: (String) -> Unit) {
        val calendarNow = Calendar.getInstance()
        val dateCheck = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
        val todayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendarNow.time)

        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->

            if (dateCheck == todayDate) {
                val minValidTime = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 5)
                }

                val selectedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }

                if (selectedTime.before(minValidTime)) {
                    val formattedMinTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(minValidTime.time)
                    Toast.makeText(requireContext(), "Vui lòng chọn giờ sau $formattedMinTime.", Toast.LENGTH_LONG).show()
                    return@OnTimeSetListener
                }
            }

            val hour = String.format("%02d", hourOfDay)
            val min = String.format("%02d", minute)
            onTimeSelected("$hour:$min")
        }

        TimePickerDialog(
            requireContext(), timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showStatusFilterDialog() {
        val statusOptions = arrayOf(
            "Đang chơi",
            "Đã hoàn thành",
            "Đã hoàn thành(manager)",
            "Đã huỷ"
        )

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Chọn Trạng thái")

        builder.setItems(statusOptions) { dialog, which ->
            currentStatusFilter = statusOptions[which]
            filterData()
            dialog.dismiss()
        }

        builder.setNegativeButton("Xóa lọc") { dialog, _ ->
            currentStatusFilter = null
            filterData()
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun showPaymentStatusFilterDialog() {
        val paymentStatusOptions = arrayOf(
            "Đã thanh toán",
            "Chưa thanh toán",
            "Thanh toán tại quầy"
        )

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Chọn Trạng thái")

        builder.setItems(paymentStatusOptions) { dialog, which ->
            currentPaymentStatusFilter = paymentStatusOptions[which]
            filterData()
            dialog.dismiss()
        }

        builder.setNegativeButton("Xóa lọc") { dialog, _ ->
            currentPaymentStatusFilter = null
            filterData()
            dialog.dismiss()
        }

        builder.create().show()

    }

    override fun onClickComplete(data: dataTableManagement) {
        // Gọi dialog tính tiền và xác nhận
        dialogConclude(data)
    }

    override fun onShowDetailsClick(data: dataTableManagement) {
        showBookingDetailsDialog(data)
    }

    override fun onEditClick(data: dataTableManagement) {
        showEditDialog(data)
    }

    override fun onDeleteClick(data: dataTableManagement) {
        dialogConfirmDelete(data)
    }


    // --- Core Action Dialogs and Logic ---

    // Hàm tính tiền (MỚI)
    private fun calculateAndShowPrice(
        data: dataTableManagement,
        onPriceCalculated: (Double, String) -> Unit
    ) {
        val now = Calendar.getInstance()
        val actualEndTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        val currentStoreId = currentStoreId ?: run {
            Toast.makeText(requireContext(), "Lỗi: Không tìm thấy Store ID.", Toast.LENGTH_SHORT).show()
            return
        }

        if (data.startTime.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Lỗi: Không tìm thấy thời gian bắt đầu.", Toast.LENGTH_LONG).show()
            return
        }

        // Bước 1: Lấy giá bàn từ Firebase
        dbRefStore.child(currentStoreId).child("priceTable").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val priceTablePerHour = snapshot.getValue(Int::class.java) ?: 0

                if (priceTablePerHour == 0) {
                    Toast.makeText(requireContext(), "Giá thuê bàn chưa được thiết lập (0).", Toast.LENGTH_LONG).show()
                }

                // Bước 2: Tính toán tổng tiền
                val calculatedMoney = calculateActualPrice(
                    data.startTime!!,
                    actualEndTimeStr,
                    priceTablePerHour
                )

                // Trả về số tiền và thời gian qua callback
                onPriceCalculated(calculatedMoney, actualEndTimeStr)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Lỗi lấy giá bàn: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // Hàm Dialog Hoàn thành (ĐÃ SỬA LOGIC)
    private fun dialogConclude(data: dataTableManagement) {
        // Bắt đầu bằng việc tính toán tiền và thời gian kết thúc thực tế
        calculateAndShowPrice(data) { calculatedMoney, actualEndTimeStr ->
            // --- BƯỚC 1: HIỂN THỊ DIALOG XÁC NHẬN ---
            val dialogView = layoutInflater.inflate(R.layout.dialog_warning, null)
            val alertDialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            val tvWarningMessage = dialogView.findViewById<TextView>(R.id.tvWarningMessage)
            val tvPriceValue = dialogView.findViewById<TextView>(R.id.tvPriceValue)
            val btnWarningClose = dialogView.findViewById<TextView>(R.id.btnWarningClose)
            val tvPrice = dialogView.findViewById<TextView>(R.id.tvPrice)
            val tvLinePrice = dialogView.findViewById<TextView>(R.id.tvLinePrice)
            val btnYes = dialogView.findViewById<Button>(R.id.btnYes)

            tvPrice.visibility = View.VISIBLE
            tvLinePrice.visibility = View.VISIBLE
            tvWarningMessage.text = "Xác nhận kết thúc đơn hàng của ${data.name}?"
            btnYes.text = "Hoàn thành"

            // Hiển thị số tiền đã tính toán
            val moneyText = String.format(Locale.getDefault(), "%.0f VND", calculatedMoney)
            tvPriceValue.text = moneyText
            tvPriceValue.visibility = View.VISIBLE

            // --- BƯỚC 2: XỬ LÝ NHẤN NÚT XÁC NHẬN (Cập nhật lên Firebase) ---
            btnYes.setOnClickListener {
                if (data.id == null) {
                    Toast.makeText(requireContext(), "Lỗi: ID đơn hàng bị thiếu.", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                    return@setOnClickListener
                }

                // CẬP NHẬT TRẠNG THÁI VÀ TIỀN LÊN FIREBASE CHỈ KHI NGƯỜI DÙNG ĐỒNG Ý
                val dataUpdate = hashMapOf<String, Any>(
                    "endTime" to actualEndTimeStr, // Cập nhật thời gian kết thúc thực tế
                    "money" to calculatedMoney, // Cập nhật số tiền
                    "status" to "Đã hoàn thành(manager)" // Cập nhật trạng thái
                )

                dbRefTableManagement.child(data.id!!).updateChildren(dataUpdate)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Đơn hàng đã HOÀN THÀNH và tính tiền: $moneyText", Toast.LENGTH_LONG).show()
                        alertDialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Lỗi cập nhật: ${it.message}", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss()
                    }
            }

            btnWarningClose.setOnClickListener {
                alertDialog.dismiss()
            }
            alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            alertDialog.show()
        }
    }


    private fun calculateActualPrice(
        startTimeStr: String,
        endTimeStr: String,
        pricePerHour: Int
    ): Double {
        return try {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startDate = dateFormat.parse(startTimeStr)!!
            val endDate = dateFormat.parse(endTimeStr)!!
            var durationInMillis = endDate.time - startDate.time

            // Xử lý trường hợp qua đêm (EndTime < StartTime)
            if (durationInMillis < 0) {
                durationInMillis += TimeUnit.DAYS.toMillis(1)
            }

            val totalHours = durationInMillis / (1000.0 * 60.0 * 60.0)
            val calculatedPrice = pricePerHour * totalHours

            // Đảm bảo giá trị không âm
            if (calculatedPrice < 0) 0.0 else calculatedPrice

        } catch (e: Exception) {
            Log.e("CalculatePrice", "Error calculating actual price: ${e.message}")
            0.0
        }
    }

    private fun showBookingDetailsDialog(data: dataTableManagement) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_booking_details, null)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Ánh xạ và gán dữ liệu vào Dialog
        dialogView.findViewById<TextView>(R.id.tvDialogOrderId).text = "ID Đơn: ${data.id}"
        dialogView.findViewById<TextView>(R.id.tvDialogCustomerName).text =
            "Khách hàng: ${data.name} (${data.phoneNumber})"
        dialogView.findViewById<TextView>(R.id.tvDialogDate).text = "Ngày: ${data.dateTime}"
        dialogView.findViewById<TextView>(R.id.tvDialogTime).text =
            "Thời gian: ${data.startTime} - ${data.endTime} (${data.person})"
        dialogView.findViewById<TextView>(R.id.tvDialogAddress).text =
            "Địa chỉ CLB: ${data.addressClb}"

        val money = String.format("%.0f VND", data.money)
        dialogView.findViewById<TextView>(R.id.tvDialogMoney).text = "Tổng tiền: ${money}"
        dialogView.findViewById<TextView>(R.id.tvDialogStatus).text = "Trạng thái: ${data.status}"
        dialogView.findViewById<TextView>(R.id.tvDialogPaymentStatus).text =
            "Thanh toán: ${data.paymentStatus}"

        // Nút SỬA và XÓA (Tích hợp hành động vào Dialog)
        dialogView.findViewById<Button>(R.id.btnDialogEdit).setOnClickListener {
            alertDialog.dismiss()
            onEditClick(data) // Gọi lại hàm Sửa
        }
        dialogView.findViewById<Button>(R.id.btnDialogDelete).setOnClickListener {
            alertDialog.dismiss()
            onDeleteClick(data) // Gọi lại hàm Xóa
        }

        dialogView.findViewById<ImageButton>(R.id.btnCloseDialog).setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()
    }

    private fun showEditDialog(data: dataTableManagement) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_booking_details, null)

        val edtName = dialogView.findViewById<TextView>(R.id.edtCustomerName)
        val edtPhone = dialogView.findViewById<TextView>(R.id.edtCustomerPhone)
        val spinnerStatus = dialogView.findViewById<Spinner>(R.id.spinnerStatus)
        val spinnerPaymentStatus = dialogView.findViewById<Spinner>(R.id.spinnerPaymentStatus)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmBooking)

        // Khởi tạo các tùy chọn cho Spinner
        val statusOptions =
            arrayOf("Đang chơi", "Đang chờ", "Đã hoàn thành", "Đã hoàn thành(manager)", "Đã huỷ", "Delete manager")
        val paymentOptions = arrayOf(
            "Đã thanh toán",
            "Chưa thanh toán",
            "Thanh toán tại quầy",
            "Chờ thanh toán VNPay",
            "Chờ thanh toán VietQR"
        )

        // Setup Adapters
        spinnerStatus.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            statusOptions
        )
        spinnerPaymentStatus.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            paymentOptions
        )

        edtName.text = data.name
        edtPhone.text = data.phoneNumber

        spinnerStatus.setSelection(statusOptions.indexOf(data.status))
        spinnerPaymentStatus.setSelection(paymentOptions.indexOf(data.paymentStatus))

        val alertDialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        btnConfirm.setOnClickListener {
            val newName = edtName.text.toString().trim()
            val newPhone = edtPhone.text.toString().trim()
            val newStatus = spinnerStatus.selectedItem.toString()
            val newPaymentStatus = spinnerPaymentStatus.selectedItem.toString()

            if (newName.isEmpty() || newPhone.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Tên và SĐT không được để trống.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val updates = hashMapOf<String, Any>(
                "name" to newName,
                "phoneNumber" to newPhone,
                "status" to newStatus,
                "paymentStatus" to newPaymentStatus
            )

            dbRefTableManagement.child(data.id!!).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(
                        requireContext(),
                        "Cập nhật đơn hàng thành công!",
                        Toast.LENGTH_SHORT
                    ).show()
                    alertDialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(
                        requireContext(),
                        "Lỗi cập nhật: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()
    }

    private fun dialogConfirmDelete(data: dataTableManagement) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_warning, null)
        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val tvWarningMessage = dialogView.findViewById<TextView>(R.id.tvWarningMessage)
        val btnWarningClose = dialogView.findViewById<TextView>(R.id.btnWarningClose)
        val btnYes = dialogView.findViewById<Button>(R.id.btnYes)

        tvWarningMessage.text = "Bạn có chắc chắn muốn HỦY đơn hàng của ${data.name}?"
        btnYes.text = "Xác nhận Hủy"
        btnYes.setBackgroundColor(Color.RED) // Đổi màu nút Hủy

        btnYes.setOnClickListener {
            data.id?.let { id ->
                val dataUpdate = hashMapOf<String, Any>(
                    "status" to "Delete manager"
                )
                dbRefTableManagement.child(id).updateChildren(dataUpdate)
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            "Đã HỦY đơn hàng thành công! (Status: Delete manager)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            requireContext(),
                            "Lỗi hủy đơn hàng: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            alertDialog.dismiss()
        }

        btnWarningClose.setOnClickListener {
            alertDialog.dismiss()
        }
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()
    }
}
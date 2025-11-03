package org.o7planning.myapplication.customer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.o7planning.myapplication.admin.TimelineRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ViewfinderView
import okhttp3.OkHttpClient
import org.o7planning.myapplication.R
import org.o7planning.myapplication.admin.ApiService
import org.o7planning.myapplication.admin.AvailabilityRequest
import org.o7planning.myapplication.admin.AvailabilityResponse
import org.o7planning.myapplication.admin.DetailedTimelineData
import org.o7planning.myapplication.admin.PaymentRequest
import org.o7planning.myapplication.admin.PaymentResponse
import org.o7planning.myapplication.admin.TimeSlot
import org.o7planning.myapplication.admin.VietQrResponse
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.data.dataVoucher
import org.o7planning.myapplication.databinding.FragmentBooktableBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class FragmentBooktable : Fragment(), onOrderClickListener {

    private lateinit var dbRefBooktable: DatabaseReference
    private lateinit var dbRefStore: DatabaseReference
    private lateinit var dbRefUser: DatabaseReference
    private lateinit var dbRefVoucher: DatabaseReference

    private lateinit var storeValueEventListener: ValueEventListener

    private lateinit var originalStoreList: ArrayList<dataStore>
    private lateinit var displayedStoreList: ArrayList<dataStore>

    private lateinit var storeAdapter: RvClbBia

    private lateinit var mAuth: FirebaseAuth
    private var userId: String? = null
    private var userName: String? = null
    private var phoneNumberUser: String? = null
    private var emailUser: String? = null

    private var storeId: String? = null
    private var storeOwnerId: String? = null
    private var nameCLB: String? = null
    private var dataLocation: String? = null
    private var dataDate: String? = null
    private var dataStartTime: String? = null
    private var dataEndTime: String? = null
    private var dataPeople: String? = null
    private var priceTable: Int? = 0
    private var totalTables: String? = null
    private var openingHour: String? = null
    private var closingHour: String? = null
    private var distanceLimitMeters: Double = 10000.0

    private var totalPrice: Double? = 0.0
    private var discount: Double? = 0.0
    private var appliedVoucherValue: String? = null

    private var detailedTimelineData: DetailedTimelineData? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var binding: FragmentBooktableBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> fetchLocationAndSortClubs()
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> showSettingsRedirectDialog()
            else -> Toast.makeText(requireContext(), "Bạn cần cấp quyền vị trí để dùng tính năng này", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentBooktableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRefStore = FirebaseDatabase.getInstance().getReference("dataStore")
        dbRefBooktable = FirebaseDatabase.getInstance().getReference("dataBookTable")
        dbRefVoucher = FirebaseDatabase.getInstance().getReference("dataVoucher")
        mAuth = FirebaseAuth.getInstance()
        userId = mAuth.currentUser?.uid
        if (userId != null) {
            dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser").child(userId!!)
        } else {
            Log.e("Auth", "User ID is null in onViewCreated")
            return
        }

        originalStoreList = arrayListOf()
        displayedStoreList = arrayListOf()

        binding.btnConfirmBooking.isEnabled = true
        binding.hsvTimeline.visibility = View.GONE

        binding.timelineChartView.setCustomerMode(true) // Set mode Khách hàng (Xanh/Đỏ)
        binding.timelineChartView.tag = Calendar.getInstance() // Set Tag mặc định là hôm nay


        loadUserinformation()

        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        binding.tvDate.text = today
        dataDate = today

        setupDistanceFilter()
        boxSelectDate()
        boxSelectPeopel()
        boxSelectOutstandingCLB()
        setupVoucherInteraction()

        binding.textViewTimeStart.setOnClickListener { showTimePickerDialog(true) }
        binding.textViewTimeEnd.setOnClickListener { showTimePickerDialog(false) }


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        binding.btnFindNearby.setOnClickListener { requestLocationAndSort() }

        binding.btnConfirmBooking.setOnClickListener {
            val currentUser = mAuth.currentUser
            if (currentUser == null) {
                Toast.makeText(requireContext(), "Lỗi: Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Nếu chưa xác thực email
            if (!currentUser.isEmailVerified) {
                showEmailVerificationRequiredDialog(currentUser) // Hiển thị Dialog yêu cầu xác thực
                return@setOnClickListener
            }

            if (dataStartTime == null || dataEndTime == null || dataDate == null || dataPeople == null || dataLocation == null || storeId == null) {
                Toast.makeText(requireContext(), "Vui lòng chọn đầy đủ thông tin đặt bàn!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!validateExistingTimeSelection()) {
                return@setOnClickListener
            }

            showTableInformationDialog()
        }

        updatePrice()
    }

    private fun showTimePickerDialog(isStartTime: Boolean) {
        if (storeId == null || dataDate == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn CLB và Ngày trước.", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
        val isToday = dataDate == today

        var defaultHour = cal.get(Calendar.HOUR_OF_DAY)
        var defaultMinute = cal.get(Calendar.MINUTE)

        val ohStr = openingHour
        val chStr = closingHour
        if (ohStr == null || chStr == null) {
            Toast.makeText(requireContext(), "Không thể tải giờ hoạt động của CLB.", Toast.LENGTH_SHORT).show()
            return
        }

        // Nếu đã có giờ, dùng giờ đó để đặt mặc định cho Dialog
        val currentSelectedTime = if (isStartTime) dataStartTime else dataEndTime
        if (currentSelectedTime != null) {
            val parts = currentSelectedTime.split(":")
            if (parts.size == 2) {
                defaultHour = parts[0].toIntOrNull() ?: defaultHour
                defaultMinute = parts[1].toIntOrNull() ?: defaultMinute
            }
        } else {
            // Nếu chưa có giờ, làm tròn phút đến 5 phút gần nhất
            defaultMinute = (defaultMinute / 5) * 5
        }

        // Tạo TimePickerDialog
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val roundedMinute = (minute / 5).toFloat().roundToInt() * 5
            val selectedTimeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, roundedMinute)

            val selectedCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, roundedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 1. KIỂM TRA CHẶN GIỜ NGOÀI HOẠT ĐỘNG
            val isTimeValid: Boolean = if (ohStr <= chStr) {
                // Trường hợp bình thường (08:00 - 22:00)
                selectedTimeStr >= ohStr && selectedTimeStr <= chStr
            } else {
                // Trường hợp qua đêm (23:00 - 04:00)
                selectedTimeStr >= ohStr || selectedTimeStr <= chStr
            }

            if (!isTimeValid) {
                Toast.makeText(requireContext(), "Giờ chọn ($selectedTimeStr) nằm ngoài giờ hoạt động (${ohStr} - ${chStr}).", Toast.LENGTH_LONG).show()
                return@OnTimeSetListener
            }

            // 2. CHUẨN HÓA NGÀY (QUAN TRỌNG CHO VIỆC TÍNH DURATION)
            if (ohStr > chStr && selectedTimeStr <= chStr) {
                // Nếu CLB hoạt động qua đêm và giờ chọn nằm trong khoảng đóng cửa (ví dụ 01:00) -> set thành ngày mai
                selectedCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            // Nếu không phải hôm nay và có chọn ngày, set ngày cho Calendar (chủ yếu cho tính duration End Time)
            if (!isToday && dataDate != null) {
                try {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val selectedDateObj = sdf.parse(dataDate!!)!!
                    selectedCalendar.time = selectedDateObj
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                    selectedCalendar.set(Calendar.MINUTE, roundedMinute)
                } catch (e: Exception) {
                    Log.e("TimePicker", "Lỗi parse ngày cho duration: ${e.message}")
                }
            }


            if (isStartTime) {
                // --- LOGIC GIỜ BẮT ĐẦU: CHẶN QUÁ KHỨ VÀ CỘNG THÊM 5 PHÚT ---
                if (isToday) {
                    val minValidTime = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }

                    if (selectedCalendar.before(minValidTime)) {
                        val formattedMinTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(minValidTime.time)
                        Toast.makeText(requireContext(), "Giờ bắt đầu phải sau ${formattedMinTime} (tối thiểu 5 phút sau hiện tại).", Toast.LENGTH_LONG).show()
                        return@OnTimeSetListener
                    }
                }

                dataStartTime = selectedTimeStr
                binding.textViewTimeStart.text = dataStartTime

                if (dataEndTime != null) {
                    val minEndTimeCal = selectedCalendar.clone() as Calendar
                    minEndTimeCal.add(Calendar.MINUTE, 30)

                    val endTimeParts = dataEndTime!!.split(":")
                    val endTimeCal = selectedCalendar.clone() as Calendar
                    endTimeCal.set(Calendar.HOUR_OF_DAY, endTimeParts[0].toInt())
                    endTimeCal.set(Calendar.MINUTE, endTimeParts[1].toInt())

                    if (endTimeCal.before(minEndTimeCal)) {
                        dataEndTime = null
                        binding.textViewTimeEnd.text = "End time"
                        Toast.makeText(requireContext(), "Giờ kết thúc đã bị reset vì thời gian thuê tối thiểu 30 phút.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    dataEndTime = null
                }

            } else {
                // --- LOGIC GIỜ KẾT THÚC: TỐI THIỂU 30 PHÚT ---
                if (dataStartTime == null) {
                    Toast.makeText(requireContext(), "Vui lòng chọn Giờ bắt đầu trước.", Toast.LENGTH_SHORT).show()
                    return@OnTimeSetListener
                }

                val startParts = dataStartTime!!.split(":")

                val selectedMoment = selectedCalendar.timeInMillis

                // Cần tính lại startMoment cho cùng ngày/qua đêm logic với End Time
                val calStart = selectedCalendar.clone() as Calendar
                calStart.set(Calendar.HOUR_OF_DAY, startParts[0].toInt())
                calStart.set(Calendar.MINUTE, startParts[1].toInt())

                if(selectedCalendar.get(Calendar.DAY_OF_YEAR) > cal.get(Calendar.DAY_OF_YEAR) && ohStr > chStr) {
                    val startMomentParse = try { timeFormat.parse(dataStartTime!!)!!.time } catch (e: Exception) { 0L }

                    var calculatedStartMoment = startMomentParse

                    if(selectedCalendar.get(Calendar.DAY_OF_YEAR) > Calendar.getInstance().get(Calendar.DAY_OF_YEAR)){
                        val calStartTomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                        calStartTomorrow.set(Calendar.HOUR_OF_DAY, startParts[0].toInt())
                        calStartTomorrow.set(Calendar.MINUTE, startParts[1].toInt())
                        calculatedStartMoment = calStartTomorrow.timeInMillis
                    }

                    val duration = selectedMoment - calculatedStartMoment

                    if (duration < TimeUnit.MINUTES.toMillis(30)) {
                        Toast.makeText(requireContext(), "Thời gian thuê tối thiểu 30 phút.", Toast.LENGTH_LONG).show()
                        return@OnTimeSetListener
                    }

                } else {
                    // Trường hợp bình thường hoặc Start/End cùng ngày
                    val startMomentParse = try { timeFormat.parse(dataStartTime!!)!!.time } catch (e: Exception) { 0L }
                    val duration = selectedMoment - startMomentParse

                    if (duration < TimeUnit.MINUTES.toMillis(30)) {
                        Toast.makeText(requireContext(), "Thời gian thuê tối thiểu 30 phút.", Toast.LENGTH_LONG).show()
                        return@OnTimeSetListener
                    }
                }

                dataEndTime = selectedTimeStr
                binding.textViewTimeEnd.text = dataEndTime
            }

            updatePrice()
        }

        val dialog = TimePickerDialog(
            requireContext(),
            timeSetListener,
            defaultHour,
            defaultMinute,
            true
        )
        dialog.show()
    }

    private fun loadUserinformation() {
        if(::dbRefUser.isInitialized) {
            dbRefUser.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userName = snapshot.child("name").getValue(String::class.java)
                    phoneNumberUser = snapshot.child("phoneNumber").getValue(String::class.java)
                    emailUser = snapshot.child("email").getValue(String::class.java)
                    Log.d("UserInfo", "User info loaded: Name=$userName")
                }
                override fun onCancelled(error: DatabaseError) { Log.e("UserInfo", "Failed to load user info: ${error.message}") }
            })
        } else {
            Log.e("UserInfo", "dbRefUser is not initialized.")
        }
    }

    private fun addDataOrder(orderId: String, paymentStatus: String, status: String) {
        if (userId == null || storeOwnerId == null || storeId == null || dataStartTime == null || dataEndTime == null || dataDate == null || dataPeople == null || dataLocation == null) {
            Log.e("Booking", "Cannot save order $orderId, missing required data.")
            Toast.makeText(requireContext(), "Lỗi thiếu thông tin khi tạo đơn", Toast.LENGTH_SHORT).show()
            return
        }
        val dataOrder = dataTableManagement(
            orderId,
            userId,
            storeOwnerId,
            storeId,
            phoneNumberUser,
            emailUser,
            userName ?: "N/A",
            dataStartTime!!,
            dataEndTime!!,
            dataDate!!,
            dataPeople!!,
            totalPrice ?: 0.0,
            paymentUrl = null,
            qrDataString = null,
            createdAt = System.currentTimeMillis(),
            status = status,
            paymentStatus = paymentStatus,
            addressClb = dataLocation!!
        )
        dbRefBooktable.child(orderId).setValue(dataOrder)
            .addOnSuccessListener { Log.d("Booking", "Order $orderId saved with status: $paymentStatus") }
            .addOnFailureListener { Log.e("Booking", "Error saving order $orderId: ${it.message}") }
    }

    private fun saveQrDataToBooking(orderId: String, qrDataString: String?, paymentUrl: String?) {
        val updates = mutableMapOf<String, Any?>()
        qrDataString?.let { updates["qrDataString"] = it }
        paymentUrl?.let { updates["paymentUrl"] = it }
        if (updates.isNotEmpty()) {
            dbRefBooktable.child(orderId).updateChildren(updates)
                .addOnSuccessListener { Log.d("BookingUpdate", "QR data saved for order $orderId") }
                .addOnFailureListener { Log.e("BookingUpdate", "Error saving QR data for order $orderId: ${it.message}") }
        }
    }

    // Mục đích: Hiển thị hộp thoại cho phép người dùng chọn phương thức thanh toán (VNPay, VietQR).
    private fun dialogPaymentMethod() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_method, null)
        builder.setView(dialogView)
        val alertDialog: AlertDialog = builder.create().apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

        val priceTextView = dialogView.findViewById<TextView>(R.id.tvPrice)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.paymentMethodGroup)
        val rbVnPay = dialogView.findViewById<RadioButton>(R.id.optionVnPay)
        val rbVietQr = dialogView.findViewById<RadioButton>(R.id.optionVietQr)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnPay = dialogView.findViewById<Button>(R.id.btnPay)

        priceTextView.text = String.format("%.0f VND", totalPrice ?: 0.0)

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnPay.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(requireContext(), "Vui lòng chọn phương thức thanh toán", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnPay.isEnabled = false
            btnCancel.isEnabled = false

            when (selectedId) {
                rbVnPay.id -> executeVnPayPayment(alertDialog)
                rbVietQr.id -> executeVietQrPayment(alertDialog)
            }
        }
        alertDialog.show()
    }

    // Mục đích: Tạo một mã Order ID duy nhất, có tiền tố "BIDA" cho các giao dịch VietQR.
    private fun generateVietQrOrderId(): String {
        // Mục đích: Tạo một mã Order ID duy nhất, có tiền tố "BIDA" cho các giao dịch VietQR.
        val prefix = "BIDA"
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomSuffix = (1..8).map { allowedChars.random() }.joinToString("")
        return "${prefix}${randomSuffix}"
    }

    // Mục đích: Xử lý luồng thanh toán VietQR: tạo đơn hàng, gọi API backend, hiển thị dialog QR.
    private fun executeVietQrPayment(dialog: AlertDialog) {
        val orderId = generateVietQrOrderId()
        addDataOrder(orderId, "Chờ thanh toán", "Đang chờ")

        dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = false
        dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = false
        Toast.makeText(requireContext(), "Đang tạo mã VietQR...", Toast.LENGTH_SHORT).show()
        // Khởi tạo Retrofit
        val apiService = RetrofitClient.apiService
        val request = PaymentRequest(amount = totalPrice ?: 0.0, orderId = orderId)
        // Gọi API backend để lấy chuỗi VietQR
        apiService.createVietQrData(request).enqueue(object : Callback<VietQrResponse> {
            override fun onResponse(call: Call<VietQrResponse>, response: Response<VietQrResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val qrDataString = response.body()!!.qrDataString
                    Log.d("VietQR_API", "Received VietQR string: $qrDataString")
                    saveQrDataToBooking(orderId, qrDataString, null)
                    showVietQrDialog(qrDataString, orderId, totalPrice ?: 0.0)
                    dialog.dismiss()
                } else {
                    Log.e("VietQR_API", "Server error getting VietQR string: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Lỗi server khi tạo mã VietQR.", Toast.LENGTH_SHORT).show()
                    dbRefBooktable.child(orderId).removeValue()
                    dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                    dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
                }
            }
            override fun onFailure(call: Call<VietQrResponse>, t: Throwable) {
                Log.e("VietQR_API", "Network error getting VietQR string: ${t.message}")
                Toast.makeText(requireContext(), "Lỗi kết nối lấy mã VietQR.", Toast.LENGTH_SHORT).show()
                dbRefBooktable.child(orderId).removeValue()
                dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
            }
        })
    }

    // Mục đích: Hiển thị mã VietQR, lắng nghe trạng thái thanh toán từ Firebase và cập nhật giao diện.
    private fun showVietQrDialog(qrDataString: String, orderId: String, amount: Double) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_vietqr_payment, null)
        builder.setView(dialogView).setCancelable(false)
        val qrDialog: AlertDialog = builder.create().apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

        val ivQrCode = dialogView.findViewById<ImageView>(R.id.ivVietQrCode)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvVietQrAmount)
        val btnClose = dialogView.findViewById<Button>(R.id.btnVietQrClose)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvVietQrPaymentStatus)

        tvAmount.text = String.format("%.0f VND", amount)
        tvStatus.text = "Quét mã để thanh toán"

        // Tạo và hiển thị ảnh QR
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(qrDataString, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("VietQRDialog", "Error generating QR bitmap", e)
            Toast.makeText(requireContext(), "Lỗi tạo ảnh QR", Toast.LENGTH_SHORT).show()
            qrDialog.dismiss()
            return
        }

        val paymentStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val paymentStatus = snapshot.child("paymentStatus").getValue(String::class.java)
                when (paymentStatus) {
                    "Đã thanh toán" -> {
                        tvStatus.text = "Thanh toán thành công!"
                        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                        btnClose.text = "Hoàn tất"
                        dbRefBooktable.child(orderId).removeEventListener(this)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            qrDialog.dismiss()
                            findNavController().navigate(R.id.fragment_home)
                        }, 3000)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("VietQrListener", "Error listening for payment status: ${error.message}") }
        }
        dbRefBooktable.child(orderId).addValueEventListener(paymentStatusListener)

        btnClose.setOnClickListener {
            dbRefBooktable.child(orderId).removeEventListener(paymentStatusListener)
            qrDialog.dismiss()
        }
        qrDialog.show()
    }

    private fun executeVnPayPayment(dialog: AlertDialog) {
        val orderId = dbRefBooktable.push().key
        if (orderId == null) {
            Toast.makeText(requireContext(), "Lỗi tạo mã đơn hàng", Toast.LENGTH_SHORT).show()
            dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
            dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
            return
        }
        addDataOrder(orderId, "Chờ thanh toán", "Đang chờ")

        dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = false
        dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = false
        Toast.makeText(requireContext(), "Đang tạo mã QR VNPay...", Toast.LENGTH_SHORT).show()

        // Khởi tạo Retrofit với timeout
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val apiService = RetrofitClient.apiService
        val request = PaymentRequest(amount = totalPrice ?: 0.0, orderId = orderId)

        Log.d("VNPay_API", "Sending request to create payment URL for order: $orderId")

        // Gọi API backend để lấy URL thanh toán VNPay
        apiService.createPaymentUrl(request).enqueue(object : Callback<PaymentResponse> {
            override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val paymentUrl = response.body()!!.paymentUrl
                    Log.d("VNPay_API", "Received VNPay URL successfully: $paymentUrl")
                    saveQrDataToBooking(orderId, null, paymentUrl)
                    showQrCodeDialog(paymentUrl, orderId)
                    dialog.dismiss()
                } else {
                    Log.e("VNPay_API", "Server error getting VNPay URL: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Lỗi server khi tạo link VNPay.", Toast.LENGTH_SHORT).show()
                    dbRefBooktable.child(orderId).removeValue()
                    dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                    dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
                }
            }
            override fun onFailure(call: Call<PaymentResponse>, t: Throwable) {
                Log.e("VNPay_API", "Network error getting VNPay URL: ${t.message}")
                Toast.makeText(requireContext(), "Lỗi kết nối lấy link VNPay.", Toast.LENGTH_SHORT).show()
                dbRefBooktable.child(orderId).removeValue()
                dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
            }
        })
    }

    // Mục đích: Hiển thị mã QR VNPay (dưới dạng URL), lắng nghe trạng thái thanh toán từ Firebase và cập nhật giao diện.
    private fun showQrCodeDialog(paymentUrl: String, orderId: String) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_payment, null)
        builder.setView(dialogView).setCancelable(false)
        val qrDialog: AlertDialog = builder.create().apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

        val ivQrCode = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvQrAmount)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvPaymentStatus)

        tvAmount.text = String.format("%.0f VND", totalPrice ?: 0.0)
        tvStatus.text = "Quét mã VNPay để thanh toán"

        // Tạo ảnh QR từ URL
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(paymentUrl, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("VNPayDialog", "Error generating QR bitmap", e)
            Toast.makeText(requireContext(), "Lỗi tạo ảnh QR", Toast.LENGTH_SHORT).show()
            qrDialog.dismiss()
            return
        }

        // Lắng nghe trạng thái từ Firebase (trạng thái này do endpoint /vnpay_ipn cập nhật)
        val paymentStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val paymentStatus = snapshot.child("paymentStatus").getValue(String::class.java)
                when (paymentStatus) {
                    "Đã thanh toán" -> {
                        tvStatus.text = "Thanh toán thành công!"
                        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                        btnClose.text = "Hoàn tất"
                        dbRefBooktable.child(orderId).removeEventListener(this)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            qrDialog.dismiss()
                            findNavController().navigate(R.id.fragment_home)
                        }, 3000)
                    }
                    "FAILED" -> {
                        tvStatus.text = "Thanh toán thất bại!"
                        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                        btnClose.text = "Đóng"
                        dbRefBooktable.child(orderId).removeEventListener(this)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("VNPayListener", "Error listening for status: ${error.message}") }
        }
        dbRefBooktable.child(orderId).addValueEventListener(paymentStatusListener)

        btnClose.setOnClickListener {
            dbRefBooktable.child(orderId).removeEventListener(paymentStatusListener)
            qrDialog.dismiss()
        }
        qrDialog.show()
    }

    // Mục đích: Thiết lập ô nhập mã giảm giá và nút "Áp dụng".
    private fun setupVoucherInteraction() {
        // Tự điền mã nếu được truyền từ màn hình trước
        arguments?.getString("voucher")?.let { binding.edtDiscountCode.setText(it) }

        binding.btnDiscountCode.setOnClickListener {
            val codeInput = binding.edtDiscountCode.text.toString().trim()
            if (codeInput.isEmpty()) {
                if (appliedVoucherValue != null) {
                    appliedVoucherValue = null
                    discount = 0.0
                    Toast.makeText(requireContext(), "Đã xóa voucher", Toast.LENGTH_SHORT).show()
                    updatePrice()
                }
            } else {
                validateVoucherCode(codeInput)
            }
        }
    }

    // Mục đích: Truy vấn Firebase RTDB để kiểm tra mã giảm giá có tồn tại, hợp lệ và áp dụng được cho CLB đang chọn hay không.
    private fun validateVoucherCode(code: String) {
        // BƯỚC 0: KIỂM TRA ĐÃ CHỌN CLB CHƯA
        if (storeId == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn CLB trước khi áp dụng voucher!", Toast.LENGTH_SHORT).show()
            appliedVoucherValue = null
            discount = 0.0
            updatePrice()
            return
        }

        // BƯỚC 1: TRUY VẤN DỮ LIỆU BẰNG TRƯỜNG "code"
        dbRefVoucher.orderByChild("code").equalTo(code)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Mã voucher không tồn tại!", Toast.LENGTH_SHORT).show()
                        updatePrice()
                        return
                    }

                    val voucherData = snapshot.children.first().getValue(dataVoucher::class.java)

                    if (voucherData == null) {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Lỗi dữ liệu voucher.", Toast.LENGTH_SHORT).show()
                        updatePrice()
                        return
                    }

                    // 1. KIỂM TRA TRẠNG THÁI (isActive)
                    val isActiveStatus = voucherData.isActive ?: false
                    if (!isActiveStatus) {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Mã voucher đã bị khóa hoặc chưa kích hoạt.", Toast.LENGTH_SHORT).show()
                        updatePrice()
                        return
                    }

                    // 2. KIỂM TRA NGÀY HẾT HẠN (expiryDate)
                    if (isExpired(voucherData.expiryDate)) {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Mã voucher đã hết hạn sử dụng.", Toast.LENGTH_SHORT).show()
                        updatePrice()
                        return
                    }

                    // 3. KIỂM TRA GIÁ TRỊ TỐI THIỂU (minOrder)
                    val minOrder = voucherData.minOrder ?: 0L
                    if ((totalPrice ?: 0.0) < minOrder) {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Tổng giá trị đơn hàng phải đạt tối thiểu ${String.format("%,d VND", minOrder)}.", Toast.LENGTH_LONG).show()
                        updatePrice()
                        return
                    }

                    // 4. KIỂM TRA CLB ÁP DỤNG (storeId)
                    val voucherStoreId = voucherData.storeId ?: ""
                    val isApplicableToStore = when {
                        // Áp dụng cho TẤT CẢ cơ sở (storeId rỗng)
                        voucherStoreId.isEmpty() -> true
                        // Áp dụng cho cơ sở HIỆN TẠI
                        voucherStoreId == storeId -> true
                        // Không áp dụng
                        else -> false
                    }

                    if (!isApplicableToStore) {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Mã voucher không áp dụng cho CLB này.", Toast.LENGTH_SHORT).show()
                        updatePrice()
                        return
                    }

                    // --- TẤT CẢ KIỂM TRA ĐỀU PASSED ---
                    appliedVoucherValue = voucherData.discount.toString()
                    Toast.makeText(requireContext(), "Áp dụng voucher thành công!", Toast.LENGTH_SHORT).show()
                    updatePrice()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Voucher", "DB error validating voucher: ${error.message}")
                    Toast.makeText(requireContext(), "Lỗi kết nối kiểm tra voucher.", Toast.LENGTH_SHORT).show()
                    appliedVoucherValue = null
                    updatePrice()
                }
            })
    }

    // Hàm phụ trợ để kiểm tra ngày hết hạn
    private fun isExpired(expiryDateStr: String?): Boolean {
        if (expiryDateStr.isNullOrEmpty()) return true // Coi như hết hạn nếu không có ngày

        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateFormat.isLenient = false // Bắt buộc đúng format

            val expiryDate = dateFormat.parse(expiryDateStr)
            val currentDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0) // Chỉ quan tâm đến ngày
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            // Hết hạn nếu ngày hiện tại > ngày hết hạn
            currentDate.after(expiryDate)
        } catch (e: Exception) {
            Log.e("VoucherDate", "Lỗi phân tích ngày hết hạn: ${expiryDateStr}", e)
            true // Nếu lỗi, coi như hết hạn
        }
    }

    private fun boxSelectDate() {
        binding.ibLogoSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    // ✅ SỬA ĐỔI: Thống nhất dùng SimpleDateFormat cho dd/MM/yyyy
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    calendar.set(Calendar.YEAR, year); calendar.set(Calendar.MONTH, month); calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    val selectedDate = sdf.format(calendar.time)

                    binding.tvDate.text = selectedDate
                    selectDate(selectedDate)
                    if (storeId != null) {
                        // 🌟 GỌI API KHI NGÀY THAY ĐỔI
                        fetchDetailedTimelineData(storeId!!, selectedDate)
                    } else {
                        Toast.makeText(requireContext(), "Vui lòng chọn Câu Lạc Bộ trước.", Toast.LENGTH_SHORT).show()
                    }
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.minDate = calendar.timeInMillis
            datePickerDialog.show()
        }
    }

    // Mục đích: Cập nhật biến lưu ngày được chọn và hiển thị lên UI, đồng thời reset lựa chọn giờ.
    private fun selectDate(date: String) {
        dataDate = date
        dataStartTime = null
        dataEndTime = null
        binding.textViewTimeStart.text = "Start time"
        binding.textViewTimeEnd.text = "End time"
        updatePrice() // Reset giá
    }

    // Mục đích: Thiết lập RecyclerView để hiển thị danh sách các CLB Bida.
    private fun boxSelectCLB() {
        storeAdapter = RvClbBia(displayedStoreList, this)
        binding.rvClbBia.adapter = storeAdapter
        binding.rvClbBia.layoutManager = GridLayoutManager(requireContext(), 1)

        // Lắng nghe dữ liệu từ Firebase
        storeValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                originalStoreList.clear()
                if (snapshot.exists()) {
                    // Chuyển đổi snapshot thành danh sách dataStore
                    snapshot.children.mapNotNullTo(originalStoreList) { it.getValue(dataStore::class.java) }
                    displayedStoreList.clear()
                    displayedStoreList.addAll(originalStoreList)
                }
                storeAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) { Log.e("StoreList", "DB error loading stores: ${error.message}") }
        }
        dbRefStore.addValueEventListener(storeValueEventListener)
    }

    // Mục đích: Hiển thị hộp thoại (Dialog) chứa thông tin chi tiết của CLB được chọn.
    private fun dialogViewStore(item: dataStore) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_store, null)
        builder.setView(dialogView).setCancelable(true)
        val alertDialog: AlertDialog = builder.create().apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

        // Gán dữ liệu vào các TextView
        dialogView.findViewById<TextView>(R.id.nameBar).text = item.name ?: "N/A"
        dialogView.findViewById<TextView>(R.id.locationBar).text = item.address ?: "N/A"
        dialogView.findViewById<TextView>(R.id.phoneBar).text = "SĐT: ${item.phone ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.emailBar).text = "Email: ${item.email ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.timeBar).text = "Giờ mở cửa: ${item.openingHour ?: "N/A"} - ${item.closingHour ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tableBar).text = "Tổng số bàn: ${item.tableNumber ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.desBar).text = item.des ?: "Không có mô tả"

        // Nút đóng dialog
        dialogView.findViewById<ImageButton>(R.id.btnExit).setOnClickListener { alertDialog.dismiss() }

        // Nút chỉ đường
        dialogView.findViewById<Button>(R.id.btnDirections).setOnClickListener {
            if (item.latitude != null && item.longitude != null) {
                launchGoogleMapsDirections(item.latitude!!, item.longitude!!, item.name ?: "CLB Bida")
            } else {
                Toast.makeText(requireContext(), "CLB này chưa cập nhật tọa độ", Toast.LENGTH_SHORT).show()
            }
        }
        alertDialog.show()
    }

    // Mục đích: Hàm callback được gọi khi người dùng click vào một CLB trong RecyclerView.
    override fun onOrderClick(id: String, ownerId: String, name: String, address: String) {
        // Lưu thông tin CLB vừa chọn
        storeId = id
        storeOwnerId = ownerId
        nameCLB = name
        dataLocation = address

        loadTableCountForSelectedStore(id)
    }

    // Mục đích: Cập nhật các biến dataStartTime, dataEndTime và các TextView hiển thị giờ/giá dựa trên các slot đang được chọn.
    private fun updateFragmentStateFromSelection() {
        // Dùng cho logic cũ, giờ chỉ cập nhật giá
        if (dataStartTime != null && dataEndTime != null) {
            binding.textViewTimeStart.text = dataStartTime
            binding.textViewTimeEnd.text = dataEndTime
        } else {
            binding.textViewTimeStart.text = "Start time"
            binding.textViewTimeEnd.text = "End time"
        }
        updatePrice()
    }

    // 🌟 HÀM GỌI API LẤY DETAILED TIMELINE (TỪ FRAGMENT OWNER)
    private fun fetchDetailedTimelineData(storeId: String, date: String) {
        Toast.makeText(context, "Đang tải Timeline chi tiết ngày $date...", Toast.LENGTH_SHORT).show()
        val request = TimelineRequest(storeId, date)

        val defaultOpen = openingHour ?: "00:00"
        val defaultClose = closingHour ?: "23:59"

        RetrofitClient.apiService.getDetailedTimelineData(request)
            .enqueue(object : Callback<DetailedTimelineData> {
                override fun onResponse(call: Call<DetailedTimelineData>, response: Response<DetailedTimelineData>) {
                    if (response.isSuccessful && response.body() != null) {
                        response.body()?.let { data ->
                            // 1. Cập nhật Timeline View (Hiển thị trực quan Xanh/Đỏ)
                            binding.timelineChartView.setCustomerMode(true)

                            // 🌟 QUAN TRỌNG: Gán lại Calendar mới cho Tag để TimelineChartView biết ngày nào đang xem
                            val chartCalendar = Calendar.getInstance().apply {
                                try { time = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(date)!! } catch (e: Exception) {}
                            }
                            binding.timelineChartView.tag = chartCalendar.clone()

                            binding.timelineChartView.updateDetailedTimeline(data)
                            detailedTimelineData = data
                            binding.hsvTimeline.visibility = View.VISIBLE
                        }
                    } else {
                        Log.e("TimelineAPI", "Server error fetching detailed timeline: ${response.code()}")
                        Toast.makeText(context, "Lỗi tải lịch bàn từ server. Code: ${response.code()}", Toast.LENGTH_SHORT).show()
                        binding.hsvTimeline.visibility = View.GONE
                        binding.timelineChartView.updateDetailedTimeline(
                            DetailedTimelineData(totalTables = 1, openingHour = defaultOpen, closingHour = defaultClose, busyBookings = emptyList())
                        )
                        detailedTimelineData = null
                    }
                }

                override fun onFailure(call: Call<DetailedTimelineData>, t: Throwable) {
                    Log.e("TimelineAPI", "Network error fetching detailed timeline: ${t.message}")
                    Toast.makeText(context, "Lỗi kết nối khi tải lịch bàn.", Toast.LENGTH_SHORT).show()
                    binding.timelineChartView.updateDetailedTimeline(
                        DetailedTimelineData(totalTables = 1, openingHour = defaultOpen, closingHour = defaultClose, busyBookings = emptyList())
                    )
                    detailedTimelineData = null
                }
            })
    }


    // Mục đích: Lấy thông tin chi tiết (tổng số bàn, giờ mở/đóng cửa, giá tiền) của CLB đã chọn từ Firebase.
    private fun loadTableCountForSelectedStore(id: String) {
        dbRefStore.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val storeData = snapshot.getValue(dataStore::class.java)
                    totalTables = storeData?.tableNumber
                    openingHour = storeData?.openingHour
                    closingHour = storeData?.closingHour
                    if (priceTable == 0 || priceTable == null) {
                        priceTable = storeData?.priceTable
                    }
                    updatePrice() // Tính lại giá với thông tin mới

                    // 🌟 KÍCH HOẠT: SAU KHI TẢI GIỜ HOẠT ĐỘNG, GỌI API TIMELINE
                    dataDate?.let { date ->
                        fetchDetailedTimelineData(id, date)
                    }

                } else {
                    Log.w("StoreDetails", "Store $id not found in DB.")
                    totalTables = "0"
                    openingHour = "00:00" // Set giá trị mặc định an toàn
                    closingHour = "23:59" // Set giá trị mặc định an toàn
                    priceTable = 0
                    updatePrice()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("StoreDetails", "DB error loading details for $id: ${error.message}")
                openingHour = "00:00" // Set giá trị mặc định an toàn
                closingHour = "23:59" // Set giá trị mặc định an toàn
            }
        })
    }

    // Mục đích: Kiểm tra xem một khung giờ (HH:mm) có nằm trong giờ hoạt động của CLB hay không.
    private fun isTimeWithInOperatingHour(selectedTime: String): Boolean {
        if (openingHour.isNullOrEmpty() || closingHour.isNullOrEmpty()) return true
        return try {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val selected = timeFormat.parse(selectedTime)!!
            val opening = timeFormat.parse(openingHour!!)!!
            val closing = timeFormat.parse(closingHour!!)!!

            if (closing.before(opening)) {
                !selected.before(opening) || !selected.after(closing)
            } else {
                !selected.before(opening) && !selected.after(closing)
            }
        } catch (e: Exception) {
            Log.w("OperatingHours", "Error parsing operating hours: ${e.message}")
            true
        }
    }

    // Mục đích: Kiểm tra xem giờ bắt đầu và giờ kết thúc người dùng chọn có nằm trong giờ hoạt động của CLB không.
    private fun validateExistingTimeSelection(): Boolean {
        // Bỏ qua nếu chưa tải được giờ hoạt động
        if (openingHour == null || closingHour == null) return true

        if (dataStartTime != null && !isTimeWithInOperatingHour(dataStartTime!!)) {
            Toast.makeText(requireContext(), "Giờ bắt đầu ($dataStartTime) nằm ngoài giờ hoạt động ($openingHour - $closingHour)", Toast.LENGTH_LONG).show()
            return false
        }
        if (dataEndTime != null && !isTimeWithInOperatingHour(dataEndTime!!)) {
            if (dataEndTime == "00:00" && (closingHour == "00:00" || closingHour == "24:00")){
                return true
            }
            Toast.makeText(requireContext(), "Giờ kết thúc ($dataEndTime) nằm ngoài giờ hoạt động ($openingHour - $closingHour)", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // Mục đích: Xử lý hiển thị ban đầu: nếu có thông tin CLB truyền từ màn hình trước thì ẩn danh sách, ngược lại thì hiện danh sách và ô tìm kiếm.
    private fun boxSelectOutstandingCLB() {
        val name = arguments?.getString("name")
        val location = arguments?.getString("location")
        val storeIdFromArgs = arguments?.getString("storeId")
        val ownerIdFromArgs = arguments?.getString("ownerId")

        if (name != null && location != null && storeIdFromArgs != null && ownerIdFromArgs != null) {
            storeId = storeIdFromArgs
            storeOwnerId = ownerIdFromArgs
            nameCLB = name
            dataLocation = location
            binding.boxSelectClb.visibility = View.GONE
            loadTableCountForSelectedStore(storeIdFromArgs)
        } else {
            binding.boxSelectClb.visibility = View.VISIBLE
            binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean { performSearch(query.orEmpty()); binding.searchView.clearFocus(); return true }
                override fun onQueryTextChange(newText: String?): Boolean { performSearch(newText.orEmpty()); return true }
            })
            boxSelectCLB()
        }
    }

    // Mục đích: Lọc danh sách CLB đang hiển thị dựa trên từ khóa người dùng nhập vào ô tìm kiếm (theo tên hoặc địa chỉ).
    private fun performSearch(query: String) {
        displayedStoreList.clear()
        if (query.isEmpty()) {
            displayedStoreList.addAll(originalStoreList)
            displayedStoreList.forEach { it.distance = null }
        } else {
            val normalizedQuery = query.lowercase(Locale.getDefault()).trim()
            val filteredList = originalStoreList.filter { store ->
                (store.name?.contains(normalizedQuery, ignoreCase = true) == true) ||
                        (store.address?.contains(normalizedQuery, ignoreCase = true) == true)
            }
            displayedStoreList.addAll(filteredList)
        }
        storeAdapter.notifyDataSetChanged()
    }

    // Mục đích: Thiết lập Spinner (dropdown) cho phép người dùng chọn số lượng người chơi.
    private fun boxSelectPeopel() {
        val peopleOptions = listOf("1 người", "2 người", "3 người", "4 người", "5+ người") // Các lựa chọn
        val spinner: Spinner = binding.spinnerPeopel
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, peopleOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                dataPeople = parent.getItemAtPosition(position).toString() // Lưu lựa chọn
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showTableInformationDialog(){
        if (dataPeople == null || dataStartTime == null || dataEndTime == null || dataDate == null || nameCLB == null || dataLocation == null || totalPrice == null) {
            Toast.makeText(requireContext(), "Thiếu thông tin, không thể hiển thị xác nhận.", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_table_information, null)
        builder.setView(dialogView)

        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvGameType = dialogView.findViewById<TextView>(R.id.txtGameType)
        val tvPeople = dialogView.findViewById<TextView>(R.id.txtManyPeoPle)
        val tvTime = dialogView.findViewById<TextView>(R.id.txtTime)
        val tvDate = dialogView.findViewById<TextView>(R.id.txtDateTime)
        val tvClubInfo = dialogView.findViewById<TextView>(R.id.txtSelectClb)
        val tvDiscount = dialogView.findViewById<TextView>(R.id.tvDiscount)
        val tvTotal = dialogView.findViewById<TextView>(R.id.PrepareTheBill)
        val btnConfirmInDialog = dialogView.findViewById<Button>(R.id.btnConfirmBooking)

        tvGameType.text = "Bida (Chung)"
        tvPeople.text = "$dataPeople"
        tvTime.text = "$dataStartTime - $dataEndTime"
        tvDate.text = "$dataDate"
        tvClubInfo.text = "Quán: $nameCLB\nCơ sở: $dataLocation"
        tvTotal.text = String.format("%.0f VND", totalPrice)
        if (discount != null && discount != 0.0) {
            tvDiscount.visibility = View.VISIBLE
            tvDiscount.text = String.format("-%.0f VND", discount)
        } else {
            tvDiscount.visibility = View.GONE
        }
        btnConfirmInDialog.setOnClickListener {
            alertDialog.dismiss()
            performFinalCheckAndBook()
        }
        val btnExit = dialogView.findViewById<ImageButton>(R.id.btnExit)
        btnExit?.setOnClickListener {
            alertDialog.dismiss()
        }
        alertDialog.show()
    }
    // Mục đích: Tính toán lại tổng giá tiền dựa trên các lựa chọn hiện tại và cập nhật các TextView hiển thị giá/giảm giá.
    private fun updatePrice() {
        if (dataStartTime != null && dataEndTime != null && priceTable != null) {
            totalPrice = calculateTablePrice()
            // Dòng này chỉ là log/debug, không cần thiết nhưng giữ lại để tránh lỗi compiler nếu có liên quan
            if (discount != 0.0){
            }else{
            }
        } else {
            totalPrice = 0.0
            discount = 0.0
        }
    }
    // Mục đích: Tính toán tổng giá tiền dựa trên thời gian chơi, giá giờ, phụ phí (nếu có) và mã giảm giá.
    private fun calculateTablePrice(): Double {
        // Mục đích: Tính toán tổng giá tiền dựa trên thời gian chơi và giá mỗi giờ, ÁP DỤNG DISCOUNT DƯỚI DẠNG PHẦN TRĂM.
        return try {
            if (dataStartTime == null || dataEndTime == null) return 0.0

            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault());
            val startDate = dateFormat.parse(dataStartTime!!)!!;
            val endDate = dateFormat.parse(dataEndTime!!)!!

            var durationInMillis = endDate.time - startDate.time
            if (durationInMillis < 0) { durationInMillis += TimeUnit.DAYS.toMillis(1) }

            val totalHours = durationInMillis / (1000.0 * 60.0 * 60.0)
            if (totalHours <= 0) return 0.0

            var calculatedPrice = (priceTable ?: 0) * totalHours
            discount = 0.0

            if (!appliedVoucherValue.isNullOrEmpty()) {
                val voucherValue = appliedVoucherValue!!

                // ✅ LOGIC SỬA ĐỔI: Luôn cố gắng xử lý như phần trăm, loại bỏ ký tự % (nếu có)
                val numericValueStr = voucherValue.replace("%", "").trim()
                val percentage = numericValueStr.toDoubleOrNull() ?: 0.0

                // Giảm giá chỉ được áp dụng nếu là một tỷ lệ hợp lý (ví dụ: < 100%)
                if (percentage > 0.0 && percentage <= 100.0) {
                    discount = calculatedPrice * (percentage / 100.0)
                    calculatedPrice -= discount!!
                } else {
                    // Xử lý là SỐ TIỀN CỐ ĐỊNH
                    discount = percentage // Lưu lại giá trị cố định
                    calculatedPrice -= discount!!
                }
            }

            if (calculatedPrice < 0) 0.0 else calculatedPrice
        } catch (e: Exception) {
            Log.e("CalculatePrice", "Error calculating price: ${e.message}"); 0.0
        }
    }
    // Mục đích: Gọi API backend (/check_availability) để kiểm tra lần cuối cùng trước khi hiển thị dialog thanh toán, nhằm xử lý race condition.
    private fun performFinalCheckAndBook() {
        // Kiểm tra lại dữ liệu cần thiết
        if (storeId == null || dataDate == null || dataStartTime == null || dataEndTime == null || totalTables == null) {
            Toast.makeText(requireContext(), "Lỗi: Thiếu thông tin để kiểm tra.", Toast.LENGTH_SHORT).show()
            binding.btnConfirmBooking.isEnabled = true
            return
        }

        binding.btnConfirmBooking.isEnabled = false
        Toast.makeText(requireContext(), "Đang kiểm tra lần cuối...", Toast.LENGTH_SHORT).show()
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // 60 giây kết nối
            .readTimeout(60, TimeUnit.SECONDS)    // 60 giây đọc dữ liệu
            .build()

        val apiService = RetrofitClient.apiService
        val request = AvailabilityRequest(
            storeId = storeId!!, dateTime = dataDate!!, startTime = dataStartTime!!,
            endTime = dataEndTime!!, totalTables = totalTables?.toIntOrNull() ?: 0
        )
        // Gọi API kiểm tra
        apiService.checkAvailability(request).enqueue(object : Callback<AvailabilityResponse> {
            override fun onResponse(call: Call<AvailabilityResponse>, response: Response<AvailabilityResponse>) {
                binding.btnConfirmBooking.isEnabled = true
                if (response.isSuccessful && response.body() != null) {
                    if (response.body()!!.available) {
                        Log.d("BookingCheck", "Final check OK. Proceeding to payment.")
                        dialogPaymentMethod()
                    } else {
                        Log.w("BookingCheck", "Final check FAILED: ${response.body()!!.message}")
                        Toast.makeText(requireContext(), response.body()!!.message ?: "Bàn đã được đặt trong lúc bạn chọn!", Toast.LENGTH_LONG).show()
                        // Tải lại Timeline trực quan khi kiểm tra cuối cùng thất bại
                        fetchDetailedTimelineData(storeId!!, dataDate!!)
                    }
                } else {
                    Log.e("BookingCheck", "Server error during final check: ${response.code()}")
                    Toast.makeText(requireContext(), "Lỗi server khi kiểm tra bàn.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<AvailabilityResponse>, t: Throwable) {
                binding.btnConfirmBooking.isEnabled = true
                Log.e("BookingCheck", "Network error during final check: ${t.message}")
                Toast.makeText(requireContext(), "Lỗi kết nối khi kiểm tra bàn.", Toast.LENGTH_SHORT).show()
            }
        })
    }
    // Mục đích: Yêu cầu quyền truy cập vị trí từ người dùng nếu chưa có.
    private fun requestLocationAndSort() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocationAndSortClubs()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Cần quyền Vị trí")
                    .setMessage("Để tìm CLB gần bạn, vui lòng cấp quyền truy cập vị trí.")
                    .setPositiveButton("OK") { _, _ -> locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }
                    .setNegativeButton("Hủy") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            else -> {
                locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }
    // Mục đích: Sử dụng FusedLocationProviderClient để lấy vị trí hiện tại của người dùng (cần có quyền).
    @SuppressLint("MissingPermission")
    private fun fetchLocationAndSortClubs() {
        Toast.makeText(context, "Đang lấy vị trí...", Toast.LENGTH_SHORT).show()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { userLocation ->
                if (userLocation != null) {
                    sortClubsByDistance(userLocation)
                } else {
                    Toast.makeText(context, "Không thể lấy vị trí hiện tại.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Log.e("Location", "Error getting location", it)
                Toast.makeText(requireContext(), "Lỗi khi lấy vị trí.", Toast.LENGTH_SHORT).show()
            }
    }

    // Mục đích: Tính khoảng cách từ vị trí người dùng đến từng CLB và sắp xếp lại danh sách hiển thị.
    private fun sortClubsByDistance(userLocation: Location?) {
        // 1. Tính khoảng cách nếu có vị trí mới
        if (userLocation != null) {
            // Cập nhật lại khoảng cách cho toàn bộ danh sách gốc
            originalStoreList.forEach { club ->
                club.distance = null
                if (club.latitude != null && club.longitude != null) {
                    val clubLocation = Location("").apply { latitude = club.latitude!!; longitude = club.longitude!! }
                    club.distance = userLocation.distanceTo(clubLocation).toDouble()
                }
            }
        }
        val filteredAndSortedList = mutableListOf<dataStore>()
        originalStoreList.forEach { club ->
            if (club.distance != null && club.distance!! <= distanceLimitMeters) {
                filteredAndSortedList.add(club)
            } else if (distanceLimitMeters == Double.MAX_VALUE) {
                filteredAndSortedList.add(club)
            }
        }

        filteredAndSortedList.sortBy { it.distance ?: Double.MAX_VALUE }
        displayedStoreList.clear()
        displayedStoreList.addAll(filteredAndSortedList)

        storeAdapter.notifyDataSetChanged() // Cập nhật RecyclerView

        val limitKm = if (distanceLimitMeters == Double.MAX_VALUE) "Tất cả" else (distanceLimitMeters / 1000.0).toInt().toString() + " km"
        if (displayedStoreList.isEmpty()) {
            binding.rvClbBia.visibility = View.GONE
            binding.tvRvClbBia.visibility = View.VISIBLE
            binding.tvRvClbBia.text = "Không tìm thấy CLB nào trong giới hạn ${limitKm}."
        } else {
            binding.tvRvClbBia.visibility = View.GONE
            binding.rvClbBia.visibility = View.VISIBLE
            if (userLocation != null) {
                Toast.makeText(context, "Đã sắp xếp ${displayedStoreList.size} CLB trong giới hạn ${limitKm}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDistanceFilter() {
        val spinner: Spinner = binding.spinnerDistanceLimit

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selection = parent.getItemAtPosition(position).toString()
                distanceLimitMeters = when (selection) {
                    "5 km" -> 5000.0
                    "10 km" -> 10000.0
                    "20 km" -> 20000.0
                    else -> Double.MAX_VALUE // "Tất cả"
                }
                if (displayedStoreList.any { it.distance != null }) {
                    sortClubsByDistance(null)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // Mục đích: Hiển thị hộp thoại hướng dẫn người dùng vào Cài đặt để cấp quyền vị trí nếu họ đã từ chối vĩnh viễn.
    private fun showSettingsRedirectDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quyền Vị trí bị từ chối")
            .setMessage("Để dùng tính năng này, bạn cần cấp quyền vị trí trong Cài đặt ứng dụng.")
            .setPositiveButton("Đi đến Cài đặt") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireActivity().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Hủy") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showEmailVerificationRequiredDialog(user: com.google.firebase.auth.FirebaseUser) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_verify, null)
        builder.setView(dialogView).setCancelable(false)
        val alertDialog: AlertDialog = builder.create().apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val btnResend = dialogView.findViewById<Button>(R.id.btn_resend_email)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_verify)

        btnResend.setOnClickListener {
            btnResend.isEnabled = false // Ngăn double-click
            user.sendEmailVerification()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Đã gửi lại email xác thực! Vui lòng kiểm tra hộp thư.", Toast.LENGTH_LONG).show()
                    alertDialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Log.e("VerifyEmail", "Failed to resend verification email", e)
                    Toast.makeText(requireContext(), "Lỗi gửi email: ${e.message}", Toast.LENGTH_LONG).show()
                    btnResend.isEnabled = true
                }
        }
        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }
        alertDialog.show()
    }
    // Mục đích: Tạo và gửi Intent để mở ứng dụng Google Maps và bắt đầu chỉ đường đến tọa độ của CLB.
    private fun launchGoogleMapsDirections(latitude: Double, longitude: Double, clubName: String) {
        val gmmIntentUri = Uri.parse("google.navigation:q=$latitude,$longitude")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(mapIntent) // Mở Google Maps
        } else {
            Toast.makeText(requireContext(), "Bạn chưa cài đặt Google Maps", Toast.LENGTH_LONG).show()
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        // Quan trọng: Gỡ bỏ listener để tránh memory leak
        if (this::storeValueEventListener.isInitialized) {
            dbRefStore.removeEventListener(storeValueEventListener)
        }
    }
}
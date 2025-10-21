package org.o7planning.myapplication.customer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.data.dataVoucher
import org.o7planning.myapplication.databinding.FragmentBooktableBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import android.widget.RadioGroup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

import com.journeyapps.barcodescanner.BarcodeEncoder
import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.SearchView
import org.o7planning.myapplication.admin.ApiService
import org.o7planning.myapplication.admin.PaymentRequest
import org.o7planning.myapplication.admin.PaymentResponse
import org.o7planning.myapplication.admin.VietQrResponse

class FragmentBooktable : Fragment(), onOrderClickListener {
    private lateinit var dbRefBooktable: DatabaseReference
    private lateinit var dbRefStore: DatabaseReference
    private lateinit var dbRefUser: DatabaseReference
    private lateinit var dbRefOverview: DatabaseReference
    private lateinit var dbRefVoucher: DatabaseReference

    private lateinit var storeValueEventListener: ValueEventListener

    private lateinit var originalStoreList: ArrayList<dataStore>
    private lateinit var displayedStoreList: ArrayList<dataStore>
    private lateinit var listBooking: ArrayList<dataTableManagement>
    private lateinit var listVoucher: ArrayList<dataVoucher>

    private lateinit var mAuth: FirebaseAuth

    private lateinit var storeAdapter: RvClbBia

    private var storeId: String? = null
    private var storeOwnerId: String? = null
    private var userId: String? = null
    private var userName: String? = null
    private var nameCLB: String? = null
    private var priceTable: Int? = 0
    private var dataGame: String? = null
    private var dataDate: String? = null
    private var dataStartTime: String? = null
    private var dataEndTime: String? = null
    private var dataPeople: String? = null
    private var dataLocation: String? = null

    private var appliedVoucherValue: String? = null// Lưu giá trị voucher ("10%" hoặc "20000")

    private var phoneNumberUser: String? = null
    private var emailUser: String? = null

    private var totalTables: String? = null
    private var openingHour: String? = null
    private var closingHour: String? = null
    private var totalPrice: Double? = 0.0

    private lateinit var binding: FragmentBooktableBinding

    // --- MỚI: Thêm các biến cho chức năng vị trí ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                fetchLocationAndSortClubs()
            }
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showSettingsRedirectDialog()
            }
            else -> {
                Toast.makeText(requireContext(), "Bạn cần cấp quyền vị trí để dùng tính năng này", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentBooktableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRefStore = FirebaseDatabase.getInstance().getReference("dataStore")
        dbRefBooktable = FirebaseDatabase.getInstance().getReference("dataBookTable")
        dbRefOverview = FirebaseDatabase.getInstance().getReference("dataOverview")
        dbRefVoucher = FirebaseDatabase.getInstance().getReference("dataVoucher")

        originalStoreList = arrayListOf()
        displayedStoreList = arrayListOf()
        listBooking = arrayListOf()
        listVoucher = arrayListOf()

        mAuth = FirebaseAuth.getInstance()
        userId = mAuth.currentUser?.uid
        dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser").child(userId!!)

        binding.btnConfirmBooking.isEnabled = true

        loadUserinformation()

        boxSelectGameType()
        boxSelectDate()
        boxSelectPeopel()
        boxSelectTime()
        boxSelectOutstandingCLB()
        setupVoucherInteraction()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        binding.btnFindNearby.setOnClickListener {
            requestLocationAndSort()
        }

        binding.btnConfirmBooking.setOnClickListener {
            if (dataStartTime == null || dataEndTime == null || dataDate == null || dataPeople == null || dataLocation == null) {
                Toast.makeText(requireContext(), "Vui lòng điền đủ thông tin đặt bàn!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!validateExistingTimeSelection()) {
                return@setOnClickListener
            }
            performFinalCheckAndBook()
        }
        updatePrice()
    }

    private fun loadUserinformation() {
        dbRefUser.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userName = snapshot.child("name").getValue(String::class.java)
                phoneNumberUser = snapshot.child("phoneNumber").getValue(String::class.java)
                emailUser = snapshot.child("email").getValue(String::class.java)
            }

            override fun onCancelled(error: DatabaseError) {
            }

        })
    }

    private fun addDataOrder(orderId: String, paymentStatus: String, status: String) {
        val dataOder = dataTableManagement(
            orderId,
            userId,
            storeOwnerId,
            storeId,
            phoneNumberUser,
            emailUser,
            userName.toString(),
            dataStartTime.toString(),
            dataEndTime.toString(),
            dataDate.toString(),
            dataPeople.toString(),
            binding.PrepareTheBill.text.toString(),
            createdAt = System.currentTimeMillis(),
            paymentStatus,
            status = status,
            dataLocation.toString()
        )
        dbRefBooktable.child(orderId).setValue(dataOder)
            .addOnSuccessListener {
                Log.d("Booking", "Lưu đơn hàng ${orderId} thành công với trạng thái: ${paymentStatus}")
            }
            .addOnFailureListener {
                Log.e("Booking", "Lỗi lưu đơn hàng ${orderId}")
            }
    }

    private fun dialogPaymentMethod() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_method, null)
        builder.setView(dialogView)
        val alertDialog: AlertDialog = builder.create()

        val priceTextView = dialogView.findViewById<TextView>(R.id.tvPrice)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.paymentMethodGroup)
        val rbVnPay = dialogView.findViewById<RadioButton>(R.id.optionVnPay)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnPay = dialogView.findViewById<Button>(R.id.btnPay)

        priceTextView.text = String.format("%.0f VND", totalPrice)

        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        btnPay.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId

            if (selectedId == -1) {
                Toast.makeText(requireContext(), "Vui lòng chọn một phương thức thanh toán", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnPay.isEnabled = false
            btnCancel.isEnabled = false

            when (selectedId) {
                rbVnPay.id -> {
                    executeVnPayPayment(alertDialog)
                }
                else -> {
                    executeVietQrPayment(alertDialog)
//                    executeCashPayment(alertDialog)
                }
            }
        }

        alertDialog.show()
    }

    private fun generateVietQrOrderId(): String {
        val prefix = "BIDA" // Bạn có thể đổi tiền tố này
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomSuffix = (1..8)
            .map { allowedChars.random() }
            .joinToString("")
        return "${prefix}${randomSuffix}"
    }

    private fun executeVietQrPayment(dialog: AlertDialog) {
        val orderId = generateVietQrOrderId()
        if (orderId == null) {
            Toast.makeText(requireContext(), "Lỗi tạo mã đơn hàng", Toast.LENGTH_SHORT).show()
            dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
            dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
            return
        }
        addDataOrder(orderId, "Chờ thanh toán VietQR", "Đã xác nhận")

        dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = false
        dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = false
        Toast.makeText(requireContext(), "Đang tạo mã VietQR...", Toast.LENGTH_SHORT).show()

        val okHttpClient = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api-datn-2025.onrender.com")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val apiService = retrofit.create(ApiService::class.java)

        // Dùng lại PaymentRequest nếu API chỉ cần amount và orderId
        val request = PaymentRequest(amount = totalPrice ?: 0.0, orderId = orderId)

        apiService.createVietQrData(request).enqueue(object : Callback<VietQrResponse> {
            override fun onResponse(call: Call<VietQrResponse>, response: Response<VietQrResponse>) {
                // 1. KIỂM TRA xem có thành công và có body không
                if (response.isSuccessful && response.body() != null) {
                    // 2. CHỈ LẤY dữ liệu NẾU thành công
                    val qrDataString = response.body()!!.qrDataString

                    Log.d("VietQR_API", "Nhận được chuỗi VietQR: $qrDataString")
                    showVietQrDialog(qrDataString, orderId, totalPrice ?: 0.0)
                    dialog.dismiss()
                } else {
                    // 3. Xử lý lỗi (nếu response không thành công, body sẽ là null)
                    Log.e("VietQR_API", "Lỗi server lấy chuỗi VietQR: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Server lỗi khi tạo mã VietQR.", Toast.LENGTH_SHORT).show()
                    // Nên xóa đơn hàng tạm đi nếu lỗi
                    dbRefBooktable.child(orderId).removeValue()
                    dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                    dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
                }
            }

            override fun onFailure(call: Call<VietQrResponse>, t: Throwable) {
                Log.e("VietQR_API", "Lỗi kết nối lấy chuỗi VietQR: ${t.message}")
                Toast.makeText(requireContext(), "Lỗi kết nối lấy mã VietQR.", Toast.LENGTH_SHORT).show()
                // Nên xóa đơn hàng tạm đi nếu lỗi
                dbRefBooktable.child(orderId).removeValue()
                dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
            }
        })
    }

    private fun showVietQrDialog(qrDataString: String, orderId: String, amount: Double) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_vietqr_payment, null)
        builder.setView(dialogView)
        builder.setCancelable(false)
        val qrDialog: AlertDialog = builder.create()

        val ivQrCode = dialogView.findViewById<ImageView>(R.id.ivVietQrCode)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvVietQrAmount)
        val btnClose = dialogView.findViewById<Button>(R.id.btnVietQrClose)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvVietQrPaymentStatus)

        tvAmount.text = String.format("%.0f VND", amount)
        tvStatus.text = "Quét mã để thanh toán"

        try {
            val barcodeEncoder = BarcodeEncoder()
            // Tạo Bitmap từ chuỗi dữ liệu server trả về
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(qrDataString, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Lỗi tạo mã QR", Toast.LENGTH_SHORT).show()
            qrDialog.dismiss()
            return
        }

        val paymentStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val paymentStatus = snapshot.child("paymentStatus").getValue(String::class.java)

                if (paymentStatus == "Đã thanh toán (VietQR)") {
                    tvStatus.text = "Thanh toán thành công!"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                    btnClose.text = "Hoàn tất"
                    dbRefBooktable.child(orderId).removeEventListener(this)

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(Runnable {
                        qrDialog.dismiss()
                        findNavController().navigate(R.id.fragment_home)
                    }, 3000)

                } else if (paymentStatus == "Thanh toán VietQR thất bại") {
                    tvStatus.text = "Thanh toán thất bại!"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    btnClose.text = "Đóng"
                    dbRefBooktable.child(orderId).removeEventListener(this)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("VietQrListener", "Lỗi lắng nghe trạng thái: ${error.message}")
            }
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
        addDataOrder(orderId, "Chờ thanh toán VNPay", "Đã xác nhận")

        dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = false
        dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = false
        Toast.makeText(requireContext(), "Đang tạo mã QR, vui lòng chờ...", Toast.LENGTH_SHORT).show()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api-datn-2025.onrender.com")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)

        val request = PaymentRequest(amount = totalPrice ?: 0.0, orderId = orderId)

        Log.d("VNPay_API", "Đang gửi yêu cầu tạo link thanh toán cho đơn hàng: $orderId")

        apiService.createPaymentUrl(request).enqueue(object : Callback<PaymentResponse> {
            override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val paymentUrl = response.body()!!.paymentUrl

                    Log.d("VNPay_API", "NHẬN ĐƯỢC URL THANH TOÁN THÀNH CÔNG:")
                    Log.d("VNPay_API", paymentUrl)

                    showQrCodeDialog(paymentUrl, orderId)
                    dialog.dismiss()
                } else {
                    Log.e("VNPay_API", "LỖI PHẢN HỒI: Mã lỗi ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Server báo lỗi. Vui lòng thử lại.", Toast.LENGTH_SHORT).show()
                    dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                    dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
                }
            }

            override fun onFailure(call: Call<PaymentResponse>, t: Throwable) {
                Log.e("VNPay_API", "LỖI KẾT NỐI: ${t.message}")
                Toast.makeText(requireContext(), "Lỗi kết nối. Vui lòng kiểm tra Internet.", Toast.LENGTH_SHORT).show()
                dialog.findViewById<Button>(R.id.btnPay)?.isEnabled = true
                dialog.findViewById<Button>(R.id.btnCancel)?.isEnabled = true
            }
        })
    }

    private fun executeCashPayment(dialog: AlertDialog) {
        val orderId = dbRefBooktable.push().key.toString()
        addDataOrder(orderId,"Thanh toán tại quầy", "Chờ xử lý")

        Toast.makeText(requireContext(), "Đặt bàn thành công! Vui lòng thanh toán tại quầy.", Toast.LENGTH_LONG).show()
        dialog.dismiss()
        findNavController().navigate(R.id.fragment_home)
    }

    private fun showQrCodeDialog(paymentUrl: String, orderId: String) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_payment, null)
        builder.setView(dialogView)
        builder.setCancelable(false)
        val qrDialog: AlertDialog = builder.create()

        val ivQrCode = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvQrAmount)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvPaymentStatus)

        tvAmount.text = String.format("%.0f VND", totalPrice)

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(paymentUrl, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Lỗi tạo mã QR", Toast.LENGTH_SHORT).show()
        }

        val paymentStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val paymentStatus = snapshot.child("paymentStatus").getValue(String::class.java)

                if (paymentStatus == "Đã thanh toán") {
                    tvStatus.text = "Thanh toán thành công!"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                    btnClose.text = "Hoàn tất"

                    dbRefBooktable.child(orderId).removeEventListener(this)

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        qrDialog.dismiss()
                        findNavController().navigate(R.id.fragment_home)
                    }, 3000)
                } else if (paymentStatus == "FAILED") {
                    tvStatus.text = "Thanh toán thất bại!"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    btnClose.text = "Thử lại"
                    dbRefBooktable.child(orderId).removeEventListener(this)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("PaymentListener", "Lỗi lắng nghe trạng thái: ${error.message}")
            }
        }
        dbRefBooktable.child(orderId).addValueEventListener(paymentStatusListener)

        btnClose.setOnClickListener {
            dbRefBooktable.child(orderId).removeEventListener(paymentStatusListener)
            qrDialog.dismiss()
        }

        qrDialog.show()
    }

    private fun calculateTablePrice(): Double {
        try {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startDate = dateFormat.parse(dataStartTime!!)
            val endDate = dateFormat.parse(dataEndTime!!)

            val durationInMillis = endDate.time - startDate.time
            val totalHours = durationInMillis / (1000 * 60 * 60).toDouble()

            var calculatedPrice = (priceTable ?: 0) * totalHours

            if (dataGame?.lowercase(Locale.getDefault()) == "carom") {
                calculatedPrice += 20000
            }

            if (!appliedVoucherValue.isNullOrEmpty()) {
                val voucherValue = appliedVoucherValue!!

                if (voucherValue.contains("%")) {
                    val percentageString = voucherValue.replace("%", "").trim()
                    val percentage = percentageString.toDoubleOrNull() ?: 0.0
                    val discountAmount = calculatedPrice * (percentage / 100.0)
                    calculatedPrice -= discountAmount
                } else {
                    val amount = voucherValue.toDoubleOrNull() ?: 0.0
                    calculatedPrice -= amount
                }
            }

            return if (calculatedPrice < 0) 0.0 else calculatedPrice

        } catch (e: Exception) {
            Log.e("CalculatePrice", "Lỗi tính toán giá: ${e.message}")
            return 0.0
        }
    }

    private fun setupVoucherInteraction() {
        val voucherFromArgs = arguments?.getString("voucher")
        if (!voucherFromArgs.isNullOrEmpty()) {
            binding.edtDiscountCode.setText(voucherFromArgs)
        }
        binding.btnDiscountCode.setOnClickListener {
            val codeInput = binding.edtDiscountCode.text.toString().trim()
            if (codeInput.isEmpty()) {
                if (appliedVoucherValue != null) {
                    appliedVoucherValue = null
                    Toast.makeText(requireContext(), "Đã xóa voucher", Toast.LENGTH_SHORT).show()
                    updatePrice()
                }
                return@setOnClickListener
            }
            validateVoucherCode(codeInput)
        }
    }

    private fun validateVoucherCode(code: String) {
        dbRefVoucher.orderByChild("voucherCode").equalTo(code)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val voucherData = snapshot.children.first().getValue(dataVoucher::class.java)
                        if (voucherData != null && isVoucherDateValid(voucherData)) {
                            appliedVoucherValue = voucherData.voucherValue
                            Toast.makeText(requireContext(), "Áp dụng voucher thành công!", Toast.LENGTH_SHORT).show()
                        } else {
                            appliedVoucherValue = null
                            Toast.makeText(requireContext(), "Mã voucher đã hết hạn!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        appliedVoucherValue = null
                        Toast.makeText(requireContext(), "Mã voucher không tồn tại!", Toast.LENGTH_SHORT).show()
                    }
                    updatePrice()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun isVoucherDateValid(voucher: dataVoucher): Boolean {
        if (voucher.voucherTimeStart.isNullOrEmpty() || voucher.voucherTimeEnd.isNullOrEmpty()) return true
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val today = Calendar.getInstance().time
            val startDate = dateFormat.parse(voucher.voucherTimeStart!!)
            val endDateCal = Calendar.getInstance().apply { time = dateFormat.parse(voucher.voucherTimeEnd!!)!! }
            endDateCal.add(Calendar.DAY_OF_YEAR, 1)
            !today.before(startDate) && today.before(endDateCal.time)
        } catch (e: Exception) {
            false
        }
    }

    private fun boxSelectTime() {
        binding.ibLogoSelectTimeStart.setOnClickListener {
            if (dataDate == null){
                Toast.makeText(requireContext(), "Vui lòng chọn ngày trước", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(
                requireContext(), { _, hourOfDay, minute ->
                    val todayCalendar = Calendar.getInstance()
                    val selectedDateCalendar = Calendar.getInstance()
                    try {
                        val sdf = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
                        selectedDateCalendar.time = sdf.parse(dataDate!!)!!
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Lỗi định dạng ngày", Toast.LENGTH_SHORT).show()
                        return@TimePickerDialog
                    }
                    val isToday = todayCalendar.get(Calendar.YEAR) == selectedDateCalendar.get(Calendar.YEAR) &&
                            todayCalendar.get(Calendar.DAY_OF_YEAR) == selectedDateCalendar.get(Calendar.DAY_OF_YEAR)
                    if (isToday) {
                        if (hourOfDay < currentHour || (hourOfDay == currentHour && minute < currentMinute)) {
                            Toast.makeText(requireContext(), "Không thể chọn giờ trong quá khứ", Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                    }
                    val selectedStartTime = String.format("%02d:%02d", hourOfDay, minute)
                    dataStartTime = selectedStartTime
                    binding.textViewTimeStart.text = dataStartTime

                    val endCalendar = Calendar.getInstance()
                    endCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    endCalendar.set(Calendar.MINUTE, minute)
                    endCalendar.add(Calendar.HOUR_OF_DAY, 1)
                    val newHour = endCalendar.get(Calendar.HOUR_OF_DAY)
                    val newMinute = endCalendar.get(Calendar.MINUTE)
                    dataEndTime = String.format("%02d:%02d", newHour, newMinute)

                    binding.textViewTimeEnd.text = dataEndTime
                    binding.txtTime.text = "Thời gian: $dataStartTime - $dataEndTime"
                    updatePrice()
                },
                currentHour,
                currentMinute,
                true
            ).show()
        }
        binding.ibLogoSelectTimeEnd.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val selectedEndTime = String.format("%02d:%02d", hourOfDay, minute)
                    if (dataStartTime != null) {
                        try {
                            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val startDate = dateFormat.parse(dataStartTime!!)
                            val endDate = dateFormat.parse(selectedEndTime)
                            val durationInMillis = endDate.time - startDate.time
                            if (durationInMillis >= 3600000) { // At least 1 hour
                                dataEndTime = selectedEndTime
                                binding.textViewTimeEnd.text = dataEndTime
                                binding.txtTime.text = "Thời gian: $dataStartTime - $dataEndTime"
                                updatePrice()
                            } else {
                                Toast.makeText(requireContext(), "Thời gian kết thúc phải lớn hơn thời gian bắt đầu ít nhất 1 giờ", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("TimePicker", "Error parsing time: ${e.message}")
                        }
                    } else {
                        Toast.makeText(requireContext(), "Vui lòng chọn thời gian bắt đầu trước", Toast.LENGTH_SHORT).show()
                    }
                },
                1,
                0,
                true
            ).show()
        }
    }

    private fun boxSelectDate() {
        binding.ibLogoSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                requireContext(), { _, year, month, dayOfMonth ->
                    val selectDate = "$dayOfMonth/${month + 1}/$year"
                    binding.tvDate.text = selectDate
                    selectDate(selectDate)
                    updatePrice()
                },
                currentYear,
                currentMonth,
                currentDay
            )
            datePickerDialog.datePicker.minDate = calendar.timeInMillis
            datePickerDialog.show()
        }
    }

    private fun selectDate(date: String) {
        dataDate = date
        binding.txtDateTime.text = "Ngày: " + dataDate
    }

    private fun boxSelectCLB() {
        storeAdapter = RvClbBia(displayedStoreList, this)
        binding.rvClbBia.adapter = storeAdapter.apply {
            onClickItem = { item, pos ->
                dialogViewStore(item)
            }
        }
        binding.rvClbBia.layoutManager = GridLayoutManager(
            requireContext(),
            1,
            GridLayoutManager.VERTICAL,
            false
        )

        storeValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                originalStoreList.clear()
                displayedStoreList.clear()
                if (snapshot.exists()) {
                    for (storeSnap in snapshot.children) {
                        val storeData = storeSnap.getValue(dataStore::class.java)
                        storeData?.let {
                            originalStoreList.add(it)
                            displayedStoreList.add(it)
                        }
                    }
                }
                storeAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
            }
        }
        dbRefStore.addValueEventListener(storeValueEventListener)
    }

    private fun dialogViewStore(item: dataStore) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_store, null)
        builder.setView(dialogView)
        builder.setCancelable(true)
        val alertDialog: AlertDialog = builder.create()
        val nameBar = dialogView.findViewById<TextView>(R.id.nameBar)
        val locationBar = dialogView.findViewById<TextView>(R.id.locationBar)
        val phoneBar = dialogView.findViewById<TextView>(R.id.phoneBar)
        val emailBar = dialogView.findViewById<TextView>(R.id.emailBar)
        val timeBar = dialogView.findViewById<TextView>(R.id.timeBar)
        val tableBar = dialogView.findViewById<TextView>(R.id.tableBar)
        val desBar = dialogView.findViewById<TextView>(R.id.desBar)
        val btnExit = dialogView.findViewById<ImageButton>(R.id.btnExit)
        nameBar.text = item.name
        locationBar.text = item.address
        phoneBar.text = "Số điện thoại: ${item.phone}"
        emailBar.text = "Email: ${item.email}"
        timeBar.text = "Thời gian hoạt động: ${item.openingHour} - ${item.closingHour}"
        tableBar.text = "Tổng số bàn: ${item.tableNumber}"
        desBar.text = item.des

        btnExit.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    override fun onOrderClick(id: String, ownerId: String, name: String, address: String) {
        storeId = id
        storeOwnerId = ownerId
        nameCLB = name
        binding.txtSelectClb.text = "Quán: $name \n Cơ sở: $address"
        dataLocation = address
        loadTableCountForSelectedStore(id)
        val selectedStore = originalStoreList.find { it.storeId == id }
        priceTable = selectedStore?.priceTable

        updatePrice()
    }

    private fun loadTableCountForSelectedStore(id: String) {
        dbRefStore.child(id)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val storeData = snapshot.getValue(dataStore::class.java)
                        totalTables = storeData?.tableNumber
                        openingHour = storeData?.openingHour
                        closingHour = storeData?.closingHour
                        if (priceTable == 0 || priceTable == null) {
                            priceTable = storeData?.priceTable
                        }
                        updatePrice()
                    } else {
                        totalTables = "0"
                        updatePrice()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
    }

    private fun isTimeWithInOperatingHour(selectedTime: String): Boolean {
        if (openingHour.isNullOrEmpty() || closingHour.isNullOrEmpty()) {
            return true
        }
        return try {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val selectedDate = timeFormat.parse(selectedTime)
            val openingDate = timeFormat.parse(openingHour!!)
            val closingDate = timeFormat.parse(closingHour!!)

            !selectedDate.before(openingDate) && !selectedDate.after(closingDate)
        } catch (e: Exception) {
            true
        }
    }

    private fun validateExistingTimeSelection(): Boolean {
        if (openingHour == null || closingHour == null) {
            return true
        }

        if (dataStartTime != null && !isTimeWithInOperatingHour(dataStartTime!!)) {
            Toast.makeText(requireContext(), "Giờ bắt đầu ($dataStartTime) nằm ngoài giờ hoạt động ($openingHour - $closingHour)", Toast.LENGTH_LONG).show()
            return false
        }

        if (dataEndTime != null && !isTimeWithInOperatingHour(dataEndTime!!)) {
            Toast.makeText(requireContext(), "Giờ kết thúc ($dataEndTime) nằm ngoài giờ hoạt động ($openingHour - $closingHour)", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

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

            binding.txtSelectClb.text = "Quán: $name \n Cơ sở: $location"
            binding.boxClbBia.visibility = View.GONE

            loadTableCountForSelectedStore(storeIdFromArgs)

        } else {
            binding.boxClbBia.visibility = View.VISIBLE
            binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    performSearch(query.orEmpty())
                    binding.searchView.clearFocus()
                    return true
                }
                override fun onQueryTextChange(newText: String?): Boolean {
                    performSearch(newText.orEmpty())
                    return true
                }
            })
            boxSelectCLB()
        }
        updatePrice()
    }

    private fun performSearch(query: String) {
        displayedStoreList.clear()
        if (query.isEmpty()) {
            displayedStoreList.addAll(originalStoreList)
            displayedStoreList.forEach { it.distance = null }
        } else {
            val normalizedQuery = query.toLowerCase(Locale.getDefault()).trim()
            val filteredList = originalStoreList.filter { store ->
                val nameMatches = store.name?.toLowerCase(Locale.getDefault())?.contains(normalizedQuery) == true
                val addressMatches = store.address?.toLowerCase(Locale.getDefault())?.contains(normalizedQuery) == true

                nameMatches || addressMatches
            }
            displayedStoreList.addAll(filteredList)
        }
        storeAdapter.notifyDataSetChanged()
    }

    private fun boxSelectPeopel() {
        val tables = listOf("1 người", "2 người", "3 người", "4 người")
        val spinnerTime: Spinner = binding.spinnerPeopel
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tables)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTime.adapter = adapter
        spinnerTime.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                p0: AdapterView<*>,
                p1: View?,
                p2: Int,
                p3: Long
            ) {
                val selectItemPeople = p0.getItemAtPosition(p2)?.toString() ?: "Không có lựa chọn"
                dataPeople = selectItemPeople
                binding.txtManyPeoPle.text = "Số lượng: $dataPeople người"
                updatePrice()
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

        }
    }


    private fun boxSelectGameType() {
        val list = mutableListOf<OutDataBookTable>()
        list.add(OutDataBookTable("8-ball"))
        list.add(OutDataBookTable("9-ball"))
        list.add(OutDataBookTable("Straight Pool"))
        list.add(OutDataBookTable("Carom"))
        list.add(OutDataBookTable("Cutthroat"))

        binding.rvOrderGame.adapter = RvBooktable(list).apply {
            onItemClick = { item, pos ->
                setGameType(item.name)
            }
        }
        binding.rvOrderGame.layoutManager = GridLayoutManager(
            requireContext(),
            2,
            GridLayoutManager.VERTICAL,
            false
        )
    }

    private fun updatePrice() {
        if (dataStartTime != null && dataEndTime != null && dataDate != null && dataPeople != null && dataLocation != null) {
            totalPrice = calculateTablePrice()
            binding.PrepareTheBill.text = "Tổng tiền: ${String.format("%.0f VND", totalPrice)}"
        } else {
            binding.PrepareTheBill.text = "Vui lòng điền đủ thông tin để tính giá"
        }
    }

    private fun setGameType(typeGame: String) {
        dataGame = typeGame
        binding.txtGameType.text = "Loại Game: $dataGame"
        updatePrice()
    }

    private fun performFinalCheckAndBook() {
        val busyStatuses = listOf("Chờ xử lý", "Đã xác nhận", "Đang chơi")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        dbRefBooktable.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val startTime = timeFormat.parse(dataStartTime!!)
                    val endTime = timeFormat.parse(dataEndTime!!)
                    var busyBookingsCount = 0

                    if (snapshot.exists()) {
                        for (bookingSnap in snapshot.children) {
                            val bookingData = bookingSnap.getValue(dataTableManagement::class.java)
                            if (bookingData != null &&
                                bookingData.status in busyStatuses &&
                                bookingData.dateTime == dataDate &&
                                bookingData.addressClb == dataLocation) {

                                val orderStart = timeFormat.parse(bookingData.startTime)
                                val orderEnd = timeFormat.parse(bookingData.endTime)
                                if (orderStart.before(endTime) && startTime.before(orderEnd)) {
                                    busyBookingsCount++
                                }
                            }
                        }
                    }

                    val totalTableCount = totalTables?.toIntOrNull() ?: 0
                    val availableCount = totalTableCount - busyBookingsCount
                    val tablesToBook = 1

                    if (availableCount >= tablesToBook) {
                        dialogPaymentMethod()
                    } else {
                        Toast.makeText(requireContext(), "Đã hết bàn vào khung giờ này. Vui lòng chọn giờ khác!", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    Log.e("BookingCheck", "Lỗi parse thời gian hoặc dữ liệu null: ${e.message}")
                    Toast.makeText(requireContext(), "Có lỗi xảy ra, vui lòng thử lại.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Lỗi kiểm tra dữ liệu: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun requestLocationAndSort() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocationAndSortClubs()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Cần quyền truy cập vị trí")
                    .setMessage("Để tìm các CLB gần bạn, ứng dụng cần quyền truy cập vị trí. Vui lòng cấp quyền khi được hỏi.")
                    .setPositiveButton("OK") { _, _ ->
                        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                    .setNegativeButton("Hủy") { dialog, _ -> dialog.dismiss() }
                    .create().show()
            }
            else -> {
                locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndSortClubs() {
        Toast.makeText(context, "Đang tìm các CLB gần bạn...", Toast.LENGTH_SHORT).show()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { userLocation ->
                if (userLocation != null) {
                    sortClubsByDistance(userLocation)
                } else {
                    Toast.makeText(context, "Không thể lấy vị trí hiện tại.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Lỗi khi lấy vị trí.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sortClubsByDistance(userLocation: Location) {
        val clubsWithLocation = displayedStoreList.filter { it.latitude != null && it.longitude != null }

        clubsWithLocation.forEach { club ->
            val clubLocation = Location("").apply {
                latitude = club.latitude!!
                longitude = club.longitude!!
            }
            club.distance = userLocation.distanceTo(clubLocation).toDouble()
        }

        val sortedClubs = clubsWithLocation.sortedBy { it.distance }

        val clubsWithoutLocation = displayedStoreList.filter { it.latitude == null || it.longitude == null }

        displayedStoreList.clear()
        displayedStoreList.addAll(sortedClubs)
        displayedStoreList.addAll(clubsWithoutLocation)

        storeAdapter.notifyDataSetChanged()

        Toast.makeText(context, "Đã cập nhật danh sách CLB gần bạn!", Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsRedirectDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quyền truy cập vị trí đã bị từ chối")
            .setMessage("Tính năng này yêu cầu quyền truy cập vị trí. Vui lòng vào cài đặt và cấp quyền cho ứng dụng.")
            .setPositiveButton("Đi đến Cài đặt") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireActivity().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Hủy") { dialog, _ -> dialog.dismiss() }
            .create().show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::storeValueEventListener.isInitialized) {
            dbRefStore.removeEventListener(storeValueEventListener)
        }
    }
}
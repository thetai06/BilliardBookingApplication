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
import android.widget.Button
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
import org.o7planning.myapplication.customer.RetrofitClient
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.FragmentTablemanagementBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FragmentTableManagement : Fragment(), setOnclickTableManagement {

    private lateinit var binding: FragmentTablemanagementBinding
    private lateinit var fullList: ArrayList<dataTableManagement> // Danh sách đầy đủ (không lọc)
    private lateinit var list: ArrayList<dataTableManagement> // Danh sách hiển thị (đã lọc)
    private lateinit var dbRefTableManagement: DatabaseReference
    private lateinit var dbRefUser: DatabaseReference
    private lateinit var tableManagementAdapter: RvTableManagement
    private lateinit var mAuth: FirebaseAuth

    private var ownerId: String? = null
    private var currentStoreId: String? = null
    private var storeOwnerId: String? = null
    private var address: String? = null

    // Biến lưu trạng thái lọc hiện tại
    private var currentStatusFilter: String? = null
    private var currentDateFilter: String? = null
    private var currentTimeFilter: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTablemanagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRefTableManagement = FirebaseDatabase.getInstance().getReference("dataBookTable")
        dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser")

        fullList = arrayListOf()
        list = arrayListOf()

        mAuth = FirebaseAuth.getInstance()
        ownerId = mAuth.currentUser?.uid

        boxTableManagement()
        // Bắt đầu bằng việc tìm storeId của người dùng
        fetchUserStoreId()
        setupFilterListeners()
        setupNewBookingListener()
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
                try {
                    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val startTimeCalendar = Calendar.getInstance()
                    startTimeCalendar.time = sdfTime.parse(time)!!

                    startTimeCalendar.add(Calendar.HOUR_OF_DAY, 3) // Thêm 3 tiếng

                    selectedEndTime = sdfTime.format(startTimeCalendar.time)
                    tvBookingEndTime.text = selectedEndTime // Cập nhật UI
                } catch (e: Exception) {
                    Log.e("NewBooking", "Lỗi tính giờ kết thúc: ${e.message}")
                    selectedEndTime = null
                    tvBookingEndTime.text = "Giờ Kết Thúc (Lỗi)"
                }
            }
        }

        tvBookingEndTime.setOnClickListener {
            showTimePicker(calendar) { time ->
                selectedEndTime = time
                tvBookingStartTime.text = time
            }
        }
        btnConfirmBooking.setOnClickListener {
            val name = edtCustomerName.text.toString().trim()
            val phone = edtCustomerPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || selectedDate.isNullOrEmpty() || selectedStartTime.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // THỰC HIỆN ĐẶT BÀN
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
                    Log.e("Order",message,null)
                }
            )
        }

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()
    }

    // Hàm trợ giúp cho Date Picker (để tránh lặp code)
    private fun showDatePicker(calendar: Calendar, onDateSelected: (String) -> Unit) {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, monthOfYear)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            onDateSelected(sdf.format(calendar.time))
        }

        DatePickerDialog(
            requireContext(), dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Hàm trợ giúp cho Time Picker (để tránh lặp code)
    private fun showTimePicker(calendar: Calendar, onTimeSelected: (String) -> Unit) {
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            val hour = String.format("%02d", hourOfDay)
            val min = String.format("%02d", minute)
            onTimeSelected("$hour:$min")
        }

        TimePickerDialog(
            requireContext(), timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24-hour format
        ).show()
    }

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
            totalTables = totalTablesToBook
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
                    onFailure("Lỗi Server: ${response.code()} ${response.errorBody()?.string() ?: response.message()}")
                }
            }

            override fun onFailure(call: Call<AvailabilityResponse>, t: Throwable) {
                onFailure("Lỗi kết nối API: ${t.message}")
            }
        })
    }

    private fun fetchUserStoreId() {
        ownerId?.let { uid ->
            dbRefUser.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentStoreId = snapshot.child("storeId").getValue(String::class.java)
                    if (currentStoreId != null) {
                        dataBoxTableManagement() // Tải dữ liệu đặt bàn cho storeId này
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

    private fun dataBoxTableManagement() {
        // Chỉ lắng nghe đơn đặt hàng có storeId trùng với storeId của người dùng
        dbRefTableManagement.orderByChild("storeId").equalTo(currentStoreId)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    fullList.clear()
                    if (snapshot.exists()){
                        for (listSnap in snapshot.children){
                            val listData = listSnap.getValue(dataTableManagement::class.java)
                            listData?.let { fullList.add(it) }
                            storeOwnerId = listData?.storeOwnerId
                            address = listData?.addressClb
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

    private fun setupFilterListeners() {
        binding.btnFilterStatus.setOnClickListener { showStatusFilterDialog() }
        binding.btnFilterDate.setOnClickListener { showDatePickerDialog() }
        binding.btnFilterTime.setOnClickListener { showTimePickerDialog() }
        binding.btnClearFilters.setOnClickListener { clearFilters() }
    }

    // Áp dụng các bộ lọc hiện tại
    private fun filterData() {
        var filtered = fullList.toList() // Bắt đầu bằng bản sao của danh sách đầy đủ

        // 1. Lọc theo trạng thái
        if (currentStatusFilter != null) {
            filtered = filtered.filter { it.status == currentStatusFilter }
            binding.btnFilterStatus.text = currentStatusFilter
        } else {
            binding.btnFilterStatus.setText("Trạng thái") // Đặt lại text mặc định
        }

        // 2. Lọc theo Ngày
        if (currentDateFilter != null) {
            filtered = filtered.filter { it.dateTime == currentDateFilter }
            binding.btnFilterDate.text = currentDateFilter
        } else {
            binding.btnFilterDate.setText("Ngày")
        }

        // 3. Lọc theo Giờ (Giả định trường 'time' tồn tại trong dataTableManagement)
        if (currentTimeFilter != null) {
            filtered = filtered.filter { it.dateTime == currentTimeFilter }
            binding.btnFilterTime.text = currentTimeFilter
        } else {
            binding.btnFilterTime.setText("Giờ")
        }

        list.clear()
        list.addAll(filtered)
        tableManagementAdapter.notifyDataSetChanged()

        // Hiển thị thông báo khi không có dữ liệu
        binding.tvNoData.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun clearFilters() {
        currentStatusFilter = null
        currentDateFilter = null
        currentTimeFilter = null
        filterData()
        Toast.makeText(requireContext(), "Đã xóa tất cả bộ lọc", Toast.LENGTH_SHORT).show()
    }

    // --- DIALOGS VÀ UI FILTERS ---

    private fun showStatusFilterDialog() {
        val statusOptions = arrayOf(
            "Đang chơi",
            "Đã hoàn thành",
            "Đã hoàn thành(owner)",
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

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, monthOfYear)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            currentDateFilter = sdf.format(calendar.time)
            filterData()
        }

        val dialog = DatePickerDialog(
            requireContext(), dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Xóa lọc") { _, _ ->
            currentDateFilter = null
            filterData()
        }

        dialog.show()
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            val hour = String.format("%02d", hourOfDay)
            val min = String.format("%02d", minute)
            currentTimeFilter = "$hour:$min" // Format HH:mm (Giả định format giờ trong DB là HH:mm)
            filterData()
        }

        val dialog = TimePickerDialog(
            requireContext(), timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24-hour format
        )

        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Xóa lọc") { _, _ ->
            currentTimeFilter = null
            filterData()
        }

        dialog.show()
    }

    override fun onClickComplete(data: dataTableManagement) {
        val id = data.id.toString()
        dialogConclude(id)
    }

    private fun dialogConclude(item: String){
        val dialogView = layoutInflater.inflate(R.layout.dialog_warning, null)
        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnWarningClose = dialogView.findViewById<TextView>(R.id.btnWarningClose)
        val btnYes = dialogView.findViewById<Button>(R.id.btnYes)

        btnYes.setOnClickListener {
            val dbRefToUpdate = dbRefTableManagement.child(item)
            val dataUpdate = hashMapOf<String, Any>(
                "status" to "Đã hoàn thành(owner)"
            )
            dbRefToUpdate.updateChildren(dataUpdate)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Hoàn thành đơn", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Không thể hoàn thành: ${it.message}", Toast.LENGTH_SHORT).show()
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
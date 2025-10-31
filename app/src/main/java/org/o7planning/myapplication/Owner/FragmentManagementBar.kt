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
import android.widget.EditText
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
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.data.dataVoucher
import org.o7planning.myapplication.databinding.FragmentManagementBarBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FragmentManagementBar : Fragment(), setOnclickManagemenrBar, onVoucherRealtimeClick {

    private lateinit var binding: FragmentManagementBarBinding

    private lateinit var dbRefManagementStore: DatabaseReference
    private lateinit var dbRefOverview: DatabaseReference
    private lateinit var dbRefVoucher: DatabaseReference
    private lateinit var dbRefUser: DatabaseReference

    private var storeValueEventListener: ValueEventListener? = null
    private var voucherValueEventListener: ValueEventListener? = null

    private lateinit var mAuth: FirebaseAuth
    private var ownerId: String? = null
    private var managerStoreId: String? = null // ID CLB của người quản lý
    private var currentStoreData: dataStore? = null // Dữ liệu CLB đang quản lý

    private lateinit var listStore: ArrayList<dataStore>
    private lateinit var stortAdapter: RvManagementBar

    private lateinit var listVoucher: ArrayList<dataVoucher>
    private lateinit var voucherAdapter: RvVoucherManagement

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentManagementBarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khởi tạo Firebase References
        dbRefManagementStore = FirebaseDatabase.getInstance().getReference("dataStore")
        dbRefOverview = FirebaseDatabase.getInstance().getReference("dataOverview")
        dbRefVoucher = FirebaseDatabase.getInstance().getReference("dataVoucher")
        dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser")

        listStore = arrayListOf()
        listVoucher = arrayListOf()

        mAuth = FirebaseAuth.getInstance()
        ownerId = mAuth.currentUser?.uid

        if (ownerId == null) {
            Toast.makeText(context, "Lỗi xác thực người dùng.", Toast.LENGTH_SHORT).show()
            return
        }

        // Setup RecyclerViews
        setupManagementBar()
        setupVoucherRecyclerView()

        binding.rvBilliardsBarManagement.visibility = View.GONE

        // Cần ẩn rvVoucherManagement cho đến khi có ID
        binding.rvVoucherManagement.visibility = View.GONE

        // Bước 1: Lấy thông tin user (storeId)
        fetchManagerStoreId()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        storeValueEventListener?.let {
            dbRefManagementStore.removeEventListener(it)
        }
        voucherValueEventListener?.let {
            dbRefVoucher.removeEventListener(it)
        }
    }

    // ============================================================================
    // BƯỚC 1: LẤY STORE ID TỪ DATAUSER
    // ============================================================================
    private fun fetchManagerStoreId() {
        ownerId?.let { uid ->
            dbRefUser.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    managerStoreId = snapshot.child("storeId").getValue(String::class.java)

                    if (!managerStoreId.isNullOrEmpty()) {
                        Log.d("ManagerInfo", "Manager StoreId: $managerStoreId")

                        // Bước 2: Load thông tin CLB
                        loadStoreInfo()

                        // Bước 3: Load vouchers của CLB này
                        loadVouchers()

                        // Hiển thị các view quản lý
                        binding.rvBilliardsBarManagement.visibility = View.VISIBLE
                        binding.rvVoucherManagement.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Bạn chưa được gán vào CLB nào để quản lý.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ManagerInfo", "Lỗi lấy thông tin user: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Lỗi tải thông tin người dùng.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    // ============================================================================
    // BƯỚC 2: LOAD THÔNG TIN CLB DỰA TRÊN STOREID
    // ============================================================================
    private fun loadStoreInfo() {
        managerStoreId?.let { storeId ->
            storeValueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val store = snapshot.getValue(dataStore::class.java)

                    if (store != null) {
                        currentStoreData = store

                        // Cập nhật list để hiển thị trong RecyclerView
                        listStore.clear()
                        listStore.add(store)
                        stortAdapter.notifyDataSetChanged()

                        // Log thông tin (để debug)
                        Log.d("StoreInfo", "Đã tải CLB: ${store.name}")

                        displayStoreInfo(store)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Không tìm thấy thông tin CLB.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("StoreInfo", "Lỗi tải thông tin CLB: ${error.message}")
                }
            }

            // Lắng nghe realtime cho CLB cụ thể
            dbRefManagementStore.child(storeId).addValueEventListener(storeValueEventListener!!)
        }
    }

    // ============================================================================
    // HIỂN THỊ THÔNG TIN CLB LÊN UI
    // ============================================================================
    private fun displayStoreInfo(store: dataStore) {
        // Cần đảm bảo các TextView này tồn tại trong FragmentManagementBarBinding
        // Ví dụ:
        // binding.tvStoreName.text = store.name
        // binding.tvStoreAddress.text = store.address
        // ...

        Toast.makeText(
            requireContext(),
            "Đang quản lý CLB: ${store.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ============================================================================
    // BƯỚC 3: LOAD VOUCHERS CÓ STOREID TRÙNG VỚI CLB (ĐÃ SỬA LỖI LỌC)
    // ============================================================================
    private fun loadVouchers() {
        voucherValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listVoucher.clear()
                val currentManagerStoreId = managerStoreId

                Log.d("LoadVouchers", "Đang tìm voucher cho CLB: $currentManagerStoreId")
                var foundCount = 0

                snapshot.children.forEach { voucherSnap ->
                    val voucherData = voucherSnap.getValue(dataVoucher::class.java)

                    if (voucherData != null) {
                        val voucherStoreId = voucherData.storeId

                        // LỌC CHÍNH XÁC: Chỉ hiển thị voucher có storeId trùng với CLB đang quản lý
                        if (voucherStoreId == currentManagerStoreId) {
                            listVoucher.add(voucherData)
                            foundCount++
                            Log.d("LoadVouchers", "✓ Đã thêm voucher: ${voucherData.code}")
                        }
                    }
                }

                voucherAdapter.notifyDataSetChanged()
                Log.d("LoadVouchers", "Tổng số voucher tìm thấy: $foundCount")

                if (foundCount == 0) {
                    Toast.makeText(
                        requireContext(),
                        "Chưa có voucher nào cho CLB này. Hãy thêm mới!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LoadVouchers", "Lỗi tải vouchers: ${error.message}")
            }
        }

        dbRefVoucher.orderByChild("storeId").equalTo(managerStoreId)
            .addValueEventListener(voucherValueEventListener!!)
    }

    // ============================================================================
    // SETUP RECYCLERVIEW
    // ============================================================================
    private fun setupManagementBar() {
        stortAdapter = RvManagementBar(listStore, this)
        binding.rvBilliardsBarManagement.adapter = stortAdapter
        binding.rvBilliardsBarManagement.layoutManager = GridLayoutManager(context, 1)
    }

    private fun setupVoucherRecyclerView() {
        voucherAdapter = RvVoucherManagement(listVoucher, this)
        binding.rvVoucherManagement.adapter = voucherAdapter
        binding.rvVoucherManagement.layoutManager = GridLayoutManager(context, 1)

        // Setup nút thêm voucher (đã được gọi trong onViewCreated)
    }

    // ============================================================================
    // DIALOG THÊM/SỬA VOUCHER
    // ============================================================================
    private fun showVoucherDialog(voucher: dataVoucher?) {
        val currentManagerStoreId = managerStoreId

        if (currentManagerStoreId.isNullOrEmpty()) {
            Toast.makeText(
                requireContext(),
                "Lỗi: Không tìm thấy ID CLB để tạo Voucher.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_voucher, null)
        builder.setView(dialogView)
        val alertDialog = builder.create()
            .apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

        val title = dialogView.findViewById<TextView>(R.id.tvVoucherTitle)
        val edtCode = dialogView.findViewById<EditText>(R.id.edtVoucherCode)
        val edtDes = dialogView.findViewById<EditText>(R.id.tvDes)
        val edtDiscount = dialogView.findViewById<EditText>(R.id.edtVoucherDiscount)
        val edtMinOrder = dialogView.findViewById<EditText>(R.id.edtVoucherMinOrder)
        val edtExpiry = dialogView.findViewById<TextView>(R.id.edtVoucherExpiry) // Hạn dùng
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmVoucher)

        setupDatePicker(edtExpiry)

        val isEditMode = voucher != null
        title.text = if (isEditMode) "Sửa Voucher" else "Thêm Voucher Mới"
        btnConfirm.text = if (isEditMode) "Lưu Thay Đổi" else "Xác nhận"

        // --- LOGIC PHÂN QUYỀN SỬA/XEM ---
        val isVoucherOfThisStore = voucher?.storeId == currentManagerStoreId
        val canEdit = !isEditMode || isVoucherOfThisStore // Luôn có thể thêm mới, chỉ cần kiểm tra khi sửa

        if (isEditMode) {
            if (!isVoucherOfThisStore) {
                // Voucher không phải của CLB mình => READ ONLY
                Toast.makeText(
                    requireContext(),
                    "Voucher này không thuộc CLB của bạn. Không thể sửa!",
                    Toast.LENGTH_LONG
                ).show()
                edtCode.isEnabled = false
                edtDes.isEnabled = false
                edtDiscount.isEnabled = false
                edtMinOrder.isEnabled = false
                edtExpiry.isEnabled = false
                btnConfirm.visibility = View.GONE
            }

            edtCode.setText(voucher!!.code)
            edtDes.setText(voucher.description)
            edtDiscount.setText(voucher.discount?.toString())
            edtMinOrder.setText(voucher.minOrder?.toString())
            edtExpiry.text = voucher.expiryDate
            edtCode.isEnabled = false
        }

        btnConfirm.setOnClickListener {
            if (!canEdit) return@setOnClickListener

            val code = edtCode.text.toString().trim()
            val des = edtDes.text.toString().trim()
            val discount = edtDiscount.text.toString().trim().toLongOrNull()
            val minOrder = edtMinOrder.text.toString().trim().toLongOrNull()
            val expiry = edtExpiry.text.toString().trim()

            // Validate
            if (code.isEmpty() || discount == null || expiry.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ Mã, Giá trị giảm và Hạn dùng.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newVoucher = dataVoucher(
                id = voucher?.id ?: dbRefVoucher.push().key,
                code = code,
                description = des,
                discount = discount,
                expiryDate = expiry,
                isActive = voucher?.isActive ?: true,
                minOrder = minOrder ?: 0L,
                storeId = currentManagerStoreId // GÁN ĐÚNG STOREID
            )

            saveVoucher(newVoucher, alertDialog)
        }

        alertDialog.show()
    }

    // ============================================================================
    // LƯU VOUCHER VÀO FIREBASE
    // ============================================================================
    private fun saveVoucher(voucher: dataVoucher, dialog: AlertDialog) {
        val voucherId = voucher.id ?: return

        dbRefVoucher.child(voucherId).setValue(voucher)
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "Lưu Voucher '${voucher.code}' thành công!",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Lưu Voucher thất bại: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("SaveVoucher", "Lỗi: ${e.message}")
            }
    }

    // ============================================================================
    // OVERRIDE CÁC INTERFACE CALLBACK
    // ============================================================================
    override fun editVoucher(dataVoucher: dataVoucher) {
        // Xử lý logic Sửa Voucher
        showVoucherDialog(dataVoucher)
    }

    override fun refuseRealtime(id: String, voucher: String) {
        // Xử lý logic Xóa Voucher (dùng hàm đã có)
        val data = listVoucher.find { it.id == id }
        if (data != null) {
            onClickDeleteVoucher(data)
        }
    }

    override fun onClickEditBar(data: dataStore) {
        // Chức năng CRUD CLB không được phép với Manager
        Toast.makeText(
            requireContext(),
            "Bạn không có quyền sửa CLB.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onClickDeleteBar(data: dataStore) {
        // Chức năng CRUD CLB không được phép với Manager
        Toast.makeText(
            requireContext(),
            "Manager không có quyền xóa CLB.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Hàm xử lý logic xóa voucher
    fun onClickDeleteVoucher(voucher: dataVoucher) {
        // Kiểm tra quyền xóa
        if (voucher.storeId != managerStoreId) {
            Toast.makeText(
                requireContext(),
                "Bạn chỉ có thể xóa Voucher của CLB mình.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Hiện dialog xác nhận
        AlertDialog.Builder(requireContext())
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa voucher '${voucher.code}'?")
            .setPositiveButton("Xóa") { _, _ ->
                val id = voucher.id ?: return@setPositiveButton

                dbRefVoucher.child(id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            "Đã xóa Voucher '${voucher.code}'",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            "Lỗi xóa Voucher: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ============================================================================
    // HÀM HỖ TRỢ - DATE/TIME PICKER
    // ============================================================================
    private fun setupTimePicker(editText: EditText) {
        editText.isFocusable = false
        editText.isFocusableInTouchMode = false
        editText.setOnClickListener { showTimePickerDialog(editText) }
    }

    private fun showTimePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            val formattedTime =
                String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            editText.setText(formattedTime)
        }, currentHour, currentMinute, true).show()
    }

    private fun setupDatePicker(textView: TextView) {
        textView.setOnClickListener { showDatePickerDialog(textView) }
    }

    private fun showDatePickerDialog(textView: TextView) {
        val calendar = Calendar.getInstance()
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, monthOfYear)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            textView.text = sdf.format(calendar.time)
        }

        DatePickerDialog(
            requireContext(), dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
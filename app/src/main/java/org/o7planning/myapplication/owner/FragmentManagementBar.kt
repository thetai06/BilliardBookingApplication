package org.o7planning.myapplication.owner

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import org.o7planning.myapplication.admin.MainActivity
import org.o7planning.myapplication.data.dataOverviewOwner
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.databinding.FragmentManagementBarBinding
import java.util.Calendar
import java.util.Locale

class FragmentManagementBar : Fragment(), setOnclickManagemenrBar {

    private lateinit var binding: FragmentManagementBarBinding
    private lateinit var dbRefManagementStore: DatabaseReference
    private lateinit var storeValueEventListener: ValueEventListener
    private lateinit var listStore: ArrayList<dataStore>
    private lateinit var stortAdapter: RvManagementBar
    private lateinit var mAuth: FirebaseAuth
    private lateinit var dbRefOverview: DatabaseReference
    private var ownerId: String? = null

    // Client để lấy vị trí
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Biến tạm để lưu tọa độ khi lấy được
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    // Biến tạm để lưu EditText mục tiêu, giúp truyền vào callback
    private var targetEdtLocation: EditText? = null

    // Trình xử lý kết quả xin quyền - ĐÃ CẬP NHẬT LOGIC
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation(targetEdtLocation)
            }
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showSettingsRedirectDialog()
            }
            else -> {
                // Người dùng từ chối nhưng chưa chọn "Don't ask again"
                Toast.makeText(requireContext(), "Bạn cần cấp quyền vị trí để dùng tính năng này", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentManagementBarBinding.inflate(inflater, container, false)
        // Khởi tạo FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRefManagementStore = FirebaseDatabase.getInstance().getReference("dataStore")
        listStore = arrayListOf()
        dbRefOverview = FirebaseDatabase.getInstance().getReference("dataOverview")
        mAuth = FirebaseAuth.getInstance()
        ownerId = mAuth.currentUser?.uid

        setupManagementBar()
        addStore()
        logOut()
    }

    private fun logOut() {
        binding.btnLogOut.setOnClickListener {
            mAuth.signOut()
            (activity as MainActivity).hideBottomNavigation()
            val navOptions = androidx.navigation.navOptions {
                popUpTo(R.id.nav_graph) {
                    inclusive = true
                }
            }
            findNavController().navigate(R.id.fragment_splash, null, navOptions)
        }
    }

    private fun addStore() {
        binding.btnAddStore.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            val dialogView = layoutInflater.inflate(R.layout.dialog_store, null)
            builder.setView(dialogView)
            val alertDialog = builder.create()
            currentLatitude = null
            currentLongitude = null

            val edtName = dialogView.findViewById<EditText>(R.id.edtName)
            val edtLocation = dialogView.findViewById<EditText>(R.id.edtLocation)
            val edtNumberPhone = dialogView.findViewById<EditText>(R.id.edtNumberPhone)
            val edtEmail = dialogView.findViewById<EditText>(R.id.edtEmail)
            val edtOpeningHour = dialogView.findViewById<EditText>(R.id.edtOpeningHour)
            val edtClosingHour = dialogView.findViewById<EditText>(R.id.edtClosingHour)
            val edtNumberTable = dialogView.findViewById<EditText>(R.id.edtNumberTable)
            val edtPriceTable = dialogView.findViewById<EditText>(R.id.edtPriceTable)
            val edtDes = dialogView.findViewById<EditText>(R.id.edtDes)
            val btnAddStore = dialogView.findViewById<Button>(R.id.btnConfirmAddStore)
            val btnGetCurrentLocation = dialogView.findViewById<EditText>(R.id.edtLocation)

            edtOpeningHour.isFocusable = false
            edtOpeningHour.isFocusableInTouchMode = false
            edtClosingHour.isFocusable = false
            edtClosingHour.isFocusableInTouchMode = false

            edtOpeningHour.setOnClickListener { showTimePickerDialog(it as EditText) }
            edtClosingHour.setOnClickListener { showTimePickerDialog(it as EditText) }

            btnGetCurrentLocation.setOnClickListener {
                requestLocationPermissionAndFetch(edtLocation)
            }

            btnAddStore.setOnClickListener {
                val name = edtName.text.toString().trim()
                val location = edtLocation.text.toString().trim()
                val phoneNumber = edtNumberPhone.text.toString().trim()
                val email = edtEmail.text.toString().trim()
                val openingHour = edtOpeningHour.text.toString().trim()
                val closingHour = edtClosingHour.text.toString().trim()
                val tableNumber = edtNumberTable.text.toString().trim()
                val priceTable = edtPriceTable.text.toString().trim().toIntOrNull() ?: 0
                val des = edtDes.text.toString().trim()

                if (location.isEmpty() || name.isEmpty() || phoneNumber.isEmpty() || email.isEmpty() || openingHour.isEmpty() || closingHour.isEmpty() || tableNumber.isEmpty()) {
                    Toast.makeText(requireContext(), "Vui lòng điền đủ thông tin bắt buộc!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val storeId = dbRefManagementStore.push().key!!

                val dataStore = dataStore(
                    storeId,
                    ownerId,
                    name,
                    R.drawable.quan_bi_a1,
                    location,
                    phoneNumber,
                    email,
                    tableNumber,
                    des,
                    openingHour,
                    closingHour,
                    priceTable,
                    latitude = currentLatitude,
                    longitude = currentLongitude
                )
                val dataOverview = dataOverviewOwner(
                    storeId,
                    ownerId,
                    totalBooking = 0,
                    confirm = 0,
                    pendingBookings = 0,
                    profit = 0.0,
                    tableActive = 0,
                    tableEmpty = 0,
                    maintenance = 0,
                )

                dbRefManagementStore.child(storeId).setValue(dataStore)
                dbRefOverview.child(storeId).setValue(dataOverview)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Thêm cửa hàng thành công!", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "thêm không thành công!", Toast.LENGTH_SHORT).show()
                    }
            }
            alertDialog.show()
        }
    }

    private fun showTimePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                editText.setText(formattedTime)
            },
            hour,
            minute,
            true
        )
        timePickerDialog.show()
    }

    private fun requestLocationPermissionAndFetch(edtLocation: EditText) {
        // Lưu lại EditText để sử dụng trong callback
        this.targetEdtLocation = edtLocation

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(edtLocation)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Cần quyền truy cập vị trí")
                    .setMessage("Để tự động lấy địa chỉ CLB, ứng dụng cần quyền truy cập vị trí của bạn. Vui lòng cấp quyền khi được hỏi.")
                    .setPositiveButton("OK") { _, _ ->
                        locationPermissionRequest.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                    .setNegativeButton("Hủy") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }
            else -> {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
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
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(edtLocation: EditText? = null) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Đang lấy vị trí...", Toast.LENGTH_SHORT).show()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location -> // 'it' refers to the location object
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        Toast.makeText(requireContext(), "Lấy vị trí thành công!", Toast.LENGTH_SHORT).show()

                        edtLocation?.let {
                            val address = getAddressFromCoordinates(location.latitude, location.longitude)
                            it.setText(address)
                        }
                    } else {
                        Toast.makeText(requireContext(), "Không thể lấy vị trí. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Lỗi khi lấy vị trí: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun getAddressFromCoordinates(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            @Suppress("DEPRECATION") // Suppress warning for older APIs
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                return addresses[0].getAddressLine(0) ?: "Không tìm thấy địa chỉ"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Lat: $latitude, Lon: $longitude"
    }

    private fun setupManagementBar() {
        stortAdapter = RvManagementBar(listStore, this)
        binding.rvBilliardsBarManagement.adapter = stortAdapter
        binding.rvBilliardsBarManagement.layoutManager = GridLayoutManager(
            requireContext(),
            1,
            GridLayoutManager.VERTICAL,
            false
        )

        storeValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listStore.clear()
                if (snapshot.exists()) {
                    for (storeSnap in snapshot.children) {
                        val storeData = storeSnap.getValue(dataStore::class.java)
                        if (storeData?.ownerId == ownerId) {
                            storeData?.let { listStore.add(it) }
                        }
                    }
                }
                stortAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
            }
        }
        dbRefManagementStore.addValueEventListener(storeValueEventListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dbRefManagementStore.removeEventListener(storeValueEventListener)
    }

    override fun onClickEditBar(data: dataStore) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_store, null)
        builder.setView(dialogView)
        val alerDialog = builder.create()
        val title = dialogView.findViewById<TextView>(R.id.title)
        val edtName = dialogView.findViewById<EditText>(R.id.edtName)
        val edtLocation = dialogView.findViewById<EditText>(R.id.edtLocation)
        val edtNumberPhone = dialogView.findViewById<EditText>(R.id.edtNumberPhone)
        val edtEmail = dialogView.findViewById<EditText>(R.id.edtEmail)
        val edtOpeningHour = dialogView.findViewById<EditText>(R.id.edtOpeningHour)
        val edtClosingHour = dialogView.findViewById<EditText>(R.id.edtClosingHour)
        val edtNumberTable = dialogView.findViewById<EditText>(R.id.edtNumberTable)
        val edtPriceTable = dialogView.findViewById<EditText>(R.id.edtPriceTable)
        val edtDes = dialogView.findViewById<EditText>(R.id.edtDes)
        val btnAddStore = dialogView.findViewById<Button>(R.id.btnConfirmAddStore)

        edtLocation.visibility = View.GONE

        edtOpeningHour.isFocusable = false
        edtOpeningHour.isFocusableInTouchMode = false
        edtClosingHour.isFocusable = false
        edtClosingHour.isFocusableInTouchMode = false
        edtOpeningHour.setOnClickListener { showTimePickerDialog(it as EditText) }
        edtClosingHour.setOnClickListener { showTimePickerDialog(it as EditText) }

        title.text = "Chỉnh sửa thông tin cửa hàng"
        edtName.setText(data.name)
        edtLocation.setText(data.address)
        edtNumberPhone.setText(data.phone)
        edtEmail.setText(data.email)
        edtOpeningHour.setText(data.openingHour)
        edtClosingHour.setText(data.closingHour)
        edtNumberTable.setText(data.tableNumber)
        edtPriceTable.setText(data.priceTable?.toInt() ?: 0)
        edtDes.setText(data.des)
        btnAddStore.setOnClickListener {
            val name = edtName.text.toString().trim()
            val location = edtLocation.text.toString().trim()
            val phoneNumber = edtNumberPhone.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val openingHour = edtOpeningHour.text.toString().trim()
            val closingHour = edtClosingHour.text.toString().trim()
            val tableNumber = edtNumberTable.text.toString().trim()
            val priceTable = edtPriceTable.text.toString().trim().toInt()
            val des = edtDes.text.toString().trim()
            updateStoreData(data.storeId, name, location, phoneNumber, email, tableNumber, priceTable, des, openingHour, closingHour, alerDialog)
        }
        alerDialog.show()
    }

    private fun updateStoreData(
        storeId: String?, name: String, location: String, phoneNumber: String, email: String, tableNumber: String, priceTable: Int?, des: String, openingHour: String, closingHour: String,
        alerDialog: AlertDialog
    ) {
        val update = hashMapOf<String, Any?>(
            "name" to name,
            "address" to location,
            "phone" to phoneNumber,
            "email" to email,
            "tableNumber" to tableNumber,
            "priceTable" to priceTable,
            "des" to des,
            "openingHour" to openingHour,
            "closingHour" to closingHour
        )

        dbRefManagementStore.child(storeId.toString()).updateChildren(update)
            .addOnSuccessListener {
                context?.let {
                    Toast.makeText(it, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                }
                alerDialog.dismiss()
            }
            .addOnFailureListener {
                context?.let {
                    Toast.makeText(it, "Lỗi cập nhật", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onClickDeleteBar(data: dataStore) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Xoá Thông Tin Cơ Sở ${data.address}")
        builder.setMessage("Bạn có chắc chắn muốn xoá cơ sở ${data.address} khỏi chuỗi cửa hàng của mình không ?")
        builder.setPositiveButton("Đồng ý") { dialog, _ ->
            deleteOders(data)
            dialog.dismiss()
        }
        builder.setNegativeButton("Không") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun deleteOders(data: dataStore) {
        val id = data.storeId.toString()
        dbRefManagementStore.child(id).removeValue()
        dbRefOverview.child(id).removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Đã xoá CLB tại chi nhánh ${data.address}!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Xoá không thành công!", Toast.LENGTH_SHORT).show()
            }
    }
}


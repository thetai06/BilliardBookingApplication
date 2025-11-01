package org.o7planning.myapplication.Owner

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.data.dataOverviewOwner
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.data.dataStoreDisplayInfo
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.FragmentOverviewBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FragmentOverview : Fragment() {

    private lateinit var binding: FragmentOverviewBinding
    private lateinit var mAuth: FirebaseAuth

    private lateinit var dbRefOverview: DatabaseReference
    private lateinit var dbRefStore: DatabaseReference
    private lateinit var dbRefBooktable: DatabaseReference
    private lateinit var dbRefUser: DatabaseReference

    private lateinit var overviewAdapter: RvOverview

    private lateinit var listOverviewDisplay: ArrayList<dataStoreDisplayInfo>

    private var currentStore: dataStore? = null
    private var currentOverview: dataOverviewOwner? = null

    private lateinit var storesListener: ValueEventListener
    private lateinit var overviewsListener: ValueEventListener

    private val statisticsListeners = mutableMapOf<String, ValueEventListener>()

    private var ownerId: String? = null
    private var userStoreId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeFirebase()
        initializeListsAndAdapters()
        setupRecyclerViews()

        attachDataListeners()
    }

    private fun initializeFirebase() {
        mAuth = FirebaseAuth.getInstance()
        ownerId = mAuth.currentUser?.uid

        dbRefOverview = FirebaseDatabase.getInstance().getReference("dataOverview")
        dbRefStore = FirebaseDatabase.getInstance().getReference("dataStore")
        dbRefBooktable = FirebaseDatabase.getInstance().getReference("dataBookTable")
        dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser")
    }

    private fun initializeListsAndAdapters() {
        listOverviewDisplay = arrayListOf()

        overviewAdapter = RvOverview(listOverviewDisplay)
    }

    private fun setupRecyclerViews() {
        binding.rvOverview.apply {
            adapter = overviewAdapter
            layoutManager = GridLayoutManager(
                requireContext(),
                1,
                GridLayoutManager.HORIZONTAL,
                false)
        }
    }

    private fun setupStatisticsListener(store: dataStore) {
        val currentStoreId = store.storeId ?: return
        val totalTables = store.tableNumber?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance()
        val todayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Biến đếm cần thiết cho giao diện:
                var totalBookingsForDay = 0
                var ownerCompletedCount = 0
                var fullyPaidCount = 0
                var currentlyPlayingCount = 0
                var totalRevenue = 0.0

                if (snapshot.exists()) {
                    for (bookingSnap in snapshot.children) {
                        val booking = bookingSnap.getValue(dataTableManagement::class.java)
                        val startTime = booking?.startTime.toString()
                        if (startTime != null) {
                            try {
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val parsedTime = timeFormat.parse(startTime)

                                val calendar = Calendar.getInstance()
                                calendar.time = parsedTime!!
                                calendar.add(Calendar.MINUTE, 5) // Cộng thêm 5 phút

                            } catch (e: Exception) {
                                Log.e("DailyStats", "Lỗi phân tích cú pháp thời gian: ${e.message}")
                            }
                        }
                        if (booking != null && booking.dateTime == todayDate) {

                            // 1. TỔNG DOANH THU & TỔNG ĐƠN HÔM NAY
                            totalRevenue += booking.money
                            totalBookingsForDay++

                            // 2. ĐÃ THANH TOÁN
                            if (booking.paymentStatus == "Đã thanh toán") {
                                fullyPaidCount++
                            }

                            // 3. CHỦ CHI NHÁNH HOÀN THÀNH ĐƠN (status = "Đã hoàn thành(owner)")
                            if (booking.status == "Đã hoàn thành(owner)") {
                                ownerCompletedCount++
                            }

                            // 4. ĐANG HOẠT ĐỘNG (status = "Đang chơi")
                            if (booking.status == "Đang chơi") {
                                currentlyPlayingCount++
                            }


                        }
                    }
                }

                // 5. TÍNH BÀN TRỐNG
                val tableEmpty = totalTables - currentlyPlayingCount

                // 6. TỔNG BÀN (Lấy trực tiếp từ dữ liệu store)
                val overviewDataMap = mapOf<String, Any>(
                    "storeId" to currentStoreId,
                    "ownerId" to (ownerId ?: ""),
                    "profit" to totalRevenue,
                    "tableEmpty" to tableEmpty,
                    "totalBooking" to totalBookingsForDay,
                    "paidBookings" to fullyPaidCount,          // Đã thanh toán
                    "simpleEnding" to ownerCompletedCount,     // Chủ chi nhánh hoàn thành
                    "tableActive" to currentlyPlayingCount,    // Đang hoạt động
                    // Giữ lại các trường cũ trong dataOverviewOwner nếu cần thiết
                )

                dbRefOverview.child(currentStoreId).updateChildren(overviewDataMap)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DailyStats", "Lỗi tải dữ liệu đặt bàn cho quán $currentStoreId: ${error.message}")
            }
        }
        statisticsListeners[currentStoreId] = listener
        dbRefBooktable.orderByChild("storeId").equalTo(currentStoreId).addValueEventListener(listener)
    }

    private fun attachDataListeners() {
        fetchUserStoreId()
    }

    private fun fetchUserStoreId() {
        ownerId?.let { uid ->
            dbRefUser.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        userStoreId = snapshot.child("storeId").getValue(String::class.java)

                        if (userStoreId != null) {
                            Log.d("Overview", "User's storeId: $userStoreId")
                            attachStoreAndOverviewListeners(userStoreId!!)
                        } else {
                            Log.w("Overview", "User does not have a storeId assigned.")
                            listOverviewDisplay.clear()
                            overviewAdapter.notifyDataSetChanged()
                            Toast.makeText(requireContext(), "Tài khoản chưa được gán cơ sở.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("Overview", "User data not found in dataUser.")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Overview", "Failed to fetch user data: ${error.message}")
                }
            })
        }
    }

    private fun attachStoreAndOverviewListeners(storeId: String) {

        storesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentStore = snapshot.getValue(dataStore::class.java)

                if (currentStore != null) {
                    removeStatisticsListeners()
                    setupStatisticsListener(currentStore!!)
                } else {
                    Log.w("Overview", "Store $storeId not found in dataStore.")
                    currentStore = null
                }
                combineAndRefreshUI()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("StoreData", "Failed to fetch store data: ${error.message}")
            }
        }
        dbRefStore.child(storeId).addValueEventListener(storesListener)

        overviewsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentOverview = snapshot.getValue(dataOverviewOwner::class.java)
                if (currentOverview == null) {
                    Log.w("OverviewData", "Overview data for $storeId not found.")
                }
                combineAndRefreshUI()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("OverviewData", "Failed to fetch overview data: ${error.message}")
            }
        }
        dbRefOverview.child(storeId).addValueEventListener(overviewsListener)
    }

    private fun combineAndRefreshUI() {
        listOverviewDisplay.clear()

        val store = currentStore
        val overview = currentOverview

        if (store != null) {
            val totalTables = store.tableNumber?.toIntOrNull() ?: 0

            val displayInfo = dataStoreDisplayInfo(
                storeId = store.storeId, ownerId = store.ownerId, name = store.name, address = store.address,
                tableNumber = store.tableNumber, // Tổng bàn
                profit = overview?.profit ?: 0.0, // Doanh thu
                tableEmpty = overview?.tableEmpty ?: totalTables, // Bàn trống
                paidBookings = overview?.paidBookings ?: 0, // Đã thanh toán
                simpleEnding = overview?.simpleEnding ?: 0, // Chủ chi nhánh hoàn thành
                tableActive = overview?.tableActive ?: 0, // Đang hoạt động
                totalBooking = overview?.totalBooking ?: 0, // Tổng đơn đặt bàn
            )
            listOverviewDisplay.add(displayInfo)
        }

        overviewAdapter.notifyDataSetChanged()
    }

    private fun removeStatisticsListeners() {
        statisticsListeners.forEach { (storeId, listener) ->
            dbRefBooktable.orderByChild("storeId").equalTo(storeId).removeEventListener(listener)
        }
        statisticsListeners.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        userStoreId?.let { storeId ->
            if (this::storesListener.isInitialized) {
                dbRefStore.child(storeId).removeEventListener(storesListener)
            }
            if (this::overviewsListener.isInitialized) {
                dbRefOverview.child(storeId).removeEventListener(overviewsListener)
            }
        }
        removeStatisticsListeners()
    }
}
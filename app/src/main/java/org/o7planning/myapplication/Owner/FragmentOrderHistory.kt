package org.o7planning.myapplication.Owner

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataStore
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.FragmentOrderHistoryBinding
import java.util.Locale

class FragmentOrderHistory : Fragment() {

    private var _binding: FragmentOrderHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private lateinit var dbRefStore: DatabaseReference
    private lateinit var dbRefBookTable: DatabaseReference

    private lateinit var orderAdapter: OrderHistoryAdapter

    private var storeList = mutableListOf<dataStore>()
    private var orderList = mutableListOf<dataTableManagement>()

    private var currentOwnerId: String? = null

    private var orderListener: ValueEventListener? = null
    private var orderQuery: Query? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentOrderHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()
        currentOwnerId = mAuth.currentUser?.uid
        dbRefStore = FirebaseDatabase.getInstance().getReference("dataStore")
        dbRefBookTable = FirebaseDatabase.getInstance().getReference("dataBookTable")

        if (currentOwnerId == null) {
            Toast.makeText(requireContext(), "Lỗi xác thực người dùng", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigate(R.id.fragment_setting)
        }

        setupOrderRecyclerView()

        loadOwnerStores(currentOwnerId!!)
    }

    private fun setupOrderRecyclerView() {
        orderAdapter = OrderHistoryAdapter(orderList)
        binding.rvOrderHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderAdapter
        }
    }

    private fun loadOwnerStores(ownerId: String) {
        binding.progressBar.visibility = View.VISIBLE
        dbRefStore.orderByChild("ownerId").equalTo(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    storeList.clear()
                    if (snapshot.exists()) {
                        for (storeSnap in snapshot.children) {
                            val store = storeSnap.getValue(dataStore::class.java)
                            if (store != null) {
                                storeList.add(store)
                            }
                        }
                        setupStoreSpinner()
                    } else {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Không tìm thấy chi nhánh nào", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Lỗi tải chi nhánh: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupStoreSpinner() {
        if (storeList.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Không có chi nhánh nào để hiển thị", Toast.LENGTH_SHORT).show()
            return
        }

        val storeDisplayNames = storeList.map { "${it.name} - ${it.address}" }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, storeDisplayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStoreSelector.adapter = adapter

        binding.spinnerStoreSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedStore = storeList[position]
                Log.d("OrderHistory", "Spinner chọn: ${selectedStore.name}")
                loadOrdersForStore(selectedStore.storeId!!)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }


    private fun loadOrdersForStore(storeId: String) {
        binding.progressBar.visibility = View.VISIBLE

        if (orderListener != null && orderQuery != null) {
            orderQuery!!.removeEventListener(orderListener!!)
        }

        orderQuery = dbRefBookTable.orderByChild("storeId").equalTo(storeId)

        orderListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                orderList.clear()
                if (snapshot.exists()) {
                    for (orderSnap in snapshot.children) {
                        val order = orderSnap.getValue(dataTableManagement::class.java)
                        if (order != null) {
                            orderList.add(order)
                        }
                    }
                    orderList.sortByDescending { it.createdAt }

                    if(orderList.isEmpty()){
                        binding.tvEmptyOrder.visibility = View.VISIBLE
                    } else {
                        binding.tvEmptyOrder.visibility = View.GONE
                    }

                } else {
                    binding.tvEmptyOrder.visibility = View.VISIBLE
                }
                orderAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Lỗi tải đơn hàng: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        orderQuery!!.addValueEventListener(orderListener!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (orderListener != null && orderQuery != null) {
            orderQuery!!.removeEventListener(orderListener!!)
        }
        _binding = null
    }

    inner class OrderHistoryAdapter(
        private val items: List<dataTableManagement>
    ) : RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder>() {

        inner class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.name_Realtime)
            val time: TextView = view.findViewById(R.id.time_Realtime)
            val address: TextView = view.findViewById(R.id.address)
            val people: TextView = view.findViewById(R.id.person_Realtime)
            val price: TextView = view.findViewById(R.id.many_Realtime)
            val status: TextView = view.findViewById(R.id.status_Realtime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order_history, parent, false)
            return OrderViewHolder(view)
        }

        override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
            val item = items[position]

            holder.name.text = "${item.name} - ${item.phoneNumber}"
            holder.time.text = "${item.dateTime} ${item.startTime} - ${item.endTime}"
            holder.address.text = item.addressClb
            holder.people.text = item.person
            holder.price.text = String.format(Locale.getDefault(), "%.0f VND", item.money ?: 0.0)
            holder.status.text = item.paymentStatus

            when (item.paymentStatus) {
                "Đã hoàn thành(owner)","Đã thanh toán" -> {
                    holder.status.setBackgroundResource(R.drawable.bg_startic_red)
                    holder.status.setTextColor(Color.WHITE)
                }
                "Đã hoàn thành" -> {
                    holder.status.setBackgroundResource(R.drawable.bg_startic)
                    holder.status.setTextColor(Color.WHITE)
                }
                "Đã huỷ" -> {
                    holder.status.setBackgroundResource(R.drawable.bg_status_red)
                    holder.status.setTextColor(Color.WHITE)
                }
                else -> {
                    holder.status.setBackgroundColor(Color.GRAY)
                    holder.status.setTextColor(Color.WHITE)
                }
            }
        }

        override fun getItemCount() = items.size
    }
}


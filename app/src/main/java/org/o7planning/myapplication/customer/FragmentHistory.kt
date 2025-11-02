package org.o7planning.myapplication.customer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.data.dataTableManagement
import org.o7planning.myapplication.databinding.FragmentHistoryBinding

class FragmentHistory : Fragment() {
    private lateinit var binding: FragmentHistoryBinding
    private lateinit var historyAdapter: RvHistory
    private lateinit var dbRefBookTable: DatabaseReference
    private lateinit var listHistory: ArrayList<dataTableManagement>
    private lateinit var historyValueEventListenner: ValueEventListener
    private lateinit var mAuth: FirebaseAuth
    private var userId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbRefBookTable = FirebaseDatabase.getInstance().getReference("dataBookTable")
        listHistory = arrayListOf()

        mAuth = FirebaseAuth.getInstance()
        userId = mAuth.currentUser?.uid

        setUpHistory()
        setUpBack()
    }

    private fun setUpBack() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setUpHistory() {
        val currentUserId = userId
        if (currentUserId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập để xem lịch sử.", Toast.LENGTH_LONG).show()
            return
        }

        historyAdapter = RvHistory(listHistory)
        binding.rvHistory.adapter = historyAdapter
        binding.rvHistory.layoutManager = GridLayoutManager(
            requireContext(),
            1,
            GridLayoutManager.VERTICAL,
            false
        )

        historyValueEventListenner = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listHistory.clear()
                if (snapshot.exists()) {
                    for (historySnap in snapshot.children) {
                        var historyData = historySnap.getValue(dataTableManagement::class.java)
                        if (historyData?.status == "Đã hoàn thành" || historyData?.status == "Đã huỷ" || historyData?.status == "Đã hoàn thành(manager)") {
                            historyData?.let { listHistory.add(it) }
                        }
                    }
                }
                historyAdapter.notifyDataSetChanged()
                if (listHistory.isEmpty()) {
                    binding.rvHistory.visibility = View.GONE
                     binding.tvNoHistory.visibility = View.VISIBLE
                } else {
                    binding.rvHistory.visibility = View.VISIBLE
                     binding.tvNoHistory.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        }
        dbRefBookTable.orderByChild("userId").equalTo(currentUserId).addValueEventListener(historyValueEventListenner)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dbRefBookTable.orderByChild("userId").equalTo(userId).removeEventListener(historyValueEventListenner)
    }


}
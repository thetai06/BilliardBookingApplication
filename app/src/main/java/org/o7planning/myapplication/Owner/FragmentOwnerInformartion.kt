package org.o7planning.myapplication.Owner

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import org.o7planning.myapplication.admin.MainActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataStoreMain
import org.o7planning.myapplication.databinding.FragmentOwnerInformartionBinding

class FragmentOwnerInformartion : Fragment() {

    private var _binding: FragmentOwnerInformartionBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private lateinit var dbRefStoreMain: DatabaseReference
    private var storeValueEventListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentOwnerInformartionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()

        val ownerId = mAuth.currentUser?.uid
        if (ownerId == null) {
            Toast.makeText(requireContext(), "Lỗi: Không tìm thấy ID chủ của hàng", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
            return
        }

        dbRefStoreMain = FirebaseDatabase.getInstance().getReference("dataStoreMain").child(ownerId)
        Log.d("FragmentOwnerInfo", "Đang tải dữ liệu cho ownerId: $ownerId")
        Log.d("FragmentOwnerInfo", "Đường dẫn Firebase: ${dbRefStoreMain.toString()}")
        loadStoreInformation()
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
            if (activity is MainActivity) {
                (activity as MainActivity).showBottomNavigation()
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
                if (activity is MainActivity) {
                    (activity as MainActivity).showBottomNavigation()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun loadStoreInformation() {
        storeValueEventListener =object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val storeData = snapshot.getValue(dataStoreMain::class.java)
                    if (storeData != null) {
                        binding.apply {
                            tvName.text = storeData.storeName
                            tvPhoneNumber.text = storeData.phoneNumber
                            tvEmail.text = storeData.email
                            tvAddress.text = storeData.address
                            tvBankAccountNumber.text = storeData.bankAccountNumber
                            tvBankName.text = storeData.bankName
                            tvPaymentStatus.text = storeData.paymentStatus
                            tvDescription.text = storeData.description
                            decodeBase64AndSetImage(storeData.businessLicenseBase64, ivBusinessLicense)
                            decodeBase64AndSetImage(storeData.nationalIdFrontBase64, ivIdFront)
                            decodeBase64AndSetImage(storeData.nationalIdBackBase64, ivIdBack)
                            btnBack.setOnClickListener {
                                findNavController().navigate(R.id.fragment_setting)
                            }
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Không tìm thấy thông tin cửa hàng", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        dbRefStoreMain.addValueEventListener(storeValueEventListener!!)
    }

    private fun decodeBase64AndSetImage(base64String: String?, imageView: ImageView) {
        if (base64String.isNullOrEmpty()) {
            Log.w("ImageDecode", "Chuỗi Base64 rỗng, dùng ảnh mặc định.")
            return
        }

        try {
            val cleanBase64 = base64String.substringAfter("base64,")

            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            imageView.setImageBitmap(decodedImage)

        } catch (e: Exception) {
            Log.e("ImageDecode", "Lỗi giải mã Base64: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (storeValueEventListener != null) {
            dbRefStoreMain.removeEventListener(storeValueEventListener!!)
        }
        _binding = null
    }

}


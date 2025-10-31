package org.o7planning.myapplication.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.admin.MainActivity
import org.o7planning.myapplication.R
import org.o7planning.myapplication.data.dataUser
import org.o7planning.myapplication.databinding.FragmentProfileBinding

class FragmentProfile : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private lateinit var dbRefUser: DatabaseReference
    private lateinit var mAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mAuth = FirebaseAuth.getInstance()
        dbRefUser = FirebaseDatabase.getInstance().getReference("dataUser")

        boxProfile()
        boxFeatureProfile()
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).showBottomNavigation()
    }

    private fun boxProfile() {
        val userId = mAuth.currentUser?.uid.toString()

        dbRefUser.child(userId).addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()){
                    val dbuser = snapshot.getValue(dataUser::class.java)
                    binding.apply {
                        nameProfile.setText(dbuser?.name)
                        emailProfile.setText(dbuser?.email)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
            }

        })

    }

    private fun boxFeatureProfile() {
        val list = mutableListOf<OutDataFeatureProfile>()
        list.add(OutDataFeatureProfile("Nâng cấp tài khoản", R.drawable.icons8pro64))
        list.add(OutDataFeatureProfile("Lịch sử đặt bàn  ", R.drawable.icons8calendar24))
//        list.add(OutDataFeatureProfile("Phương thức thanh toán", R.drawable.icons8card24))
//        list.add(OutDataFeatureProfile("Thông báo", R.drawable.icons8notification24))
        list.add(OutDataFeatureProfile("Thông tin cá nhân", R.drawable.icons8person24))
//        list.add(OutDataFeatureProfile("Cài đặt", R.drawable.icons8settings24))
        list.add(OutDataFeatureProfile("Đăng xuất", R.drawable.icons8export24))



        binding.rvFeatureProfile.adapter = RvFeatureProfile(list).apply {
            onClickItem = { item, pos ->
                handleClick(item, pos)
            }
        }
        binding.rvFeatureProfile.layoutManager = GridLayoutManager(
            requireContext(), 1, GridLayoutManager.VERTICAL, false
        )
    }
    fun handleClick(item: OutDataFeatureProfile, position: Int) {
        val navOptions = androidx.navigation.navOptions {
            popUpTo(R.id.nav_graph){
                inclusive = true
            }
        }
        when (position) {

            0 -> {
                Toast.makeText(requireContext(),"Liên hệ nhà phát triển", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                findNavController().navigate(R.id.fragment_history)
                (activity as MainActivity).hideBottomNavigation()
            }
            2 -> {
                findNavController().navigate(R.id.fragment_personal_information)
                (activity as MainActivity).hideBottomNavigation()
            }
            else  -> {
                mAuth.signOut()
                (activity as MainActivity).hideBottomNavigation()
                findNavController().navigate(R.id.fragment_splash,null,navOptions)
            }
        }
    }



}
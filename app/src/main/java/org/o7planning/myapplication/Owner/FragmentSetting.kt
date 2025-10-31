package org.o7planning.myapplication.Owner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.actionCodeSettings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.R
import org.o7planning.myapplication.admin.MainActivity
import org.o7planning.myapplication.customer.OutDataFeatureProfile
import org.o7planning.myapplication.customer.RvFeatureProfile
import org.o7planning.myapplication.data.dataUser
import org.o7planning.myapplication.databinding.FragmentSettingBinding

class FragmentSetting : Fragment() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var dbRefOwner: DatabaseReference
    private lateinit var binding: FragmentSettingBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mAuth = FirebaseAuth.getInstance()
        dbRefOwner = FirebaseDatabase.getInstance().getReference("dataUser")

        boxFeatureProfile()
        boxProfile()
    }

    override fun onResume() {
        super.onResume()
        if (activity is MainActivity){
            (activity as MainActivity).showBottomNavigation()
        }
    }

    private fun boxProfile() {
        val userId = mAuth.currentUser?.uid.toString()

        dbRefOwner.child(userId).addListenerForSingleValueEvent(object : ValueEventListener{
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
        list.add(OutDataFeatureProfile("Lịch sử đặt bàn trong ngày", R.drawable.icons8calendar24))
        list.add(OutDataFeatureProfile("Thông tin Quán", R.drawable.icons8person24))
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
                findNavController().navigate(R.id.fragment_order_history)
                (activity as MainActivity).hideBottomNavigation()
            }
            1 -> {
                findNavController().navigate(R.id.fragment_owner_informertion)
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
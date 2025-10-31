package org.o7planning.myapplication.admin

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.o7planning.myapplication.R
import org.o7planning.myapplication.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    lateinit var navController: NavController
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.visibility = View.GONE

        val colorSelect = ContextCompat.getColorStateList(this, R.color.bottom_nav_color_selector)
        binding.bottomNavigation.itemTextColor = colorSelect
        binding.bottomNavigation.itemIconTintList = colorSelect

        checkUserVerificationStatus()
    }

    fun checkUserVerificationStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        val warningBanner = findViewById<TextView>(R.id.tvWarningBanner)

        if (user == null) {
            // Không có user, ẩn banner
            warningBanner.visibility = View.GONE
            return
        }

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful) {

                val hasEmail = !user.email.isNullOrEmpty()
                val isEmailVerified = user.isEmailVerified

                if (hasEmail && !isEmailVerified) {
                    warningBanner.visibility = View.VISIBLE
                    setupVerificationBanner(warningBanner, user)
                } else {
                    warningBanner.visibility = View.GONE
                }
            } else {
                warningBanner.visibility = View.GONE
                Log.e("VerificationBanner", "User reload failed", task.exception)
            }
        }
    }

    private fun setupVerificationBanner(warningBanner: TextView, user: com.google.firebase.auth.FirebaseUser) {
        warningBanner.text = "Tài khoản chưa xác thực. Nhấn để gửi lại email."
        warningBanner.isEnabled = true // Đảm bảo banner có thể nhấn

        warningBanner.setOnClickListener {
            Log.d("VerificationBanner", "Banner đã được nhấn!")
            warningBanner.isEnabled = false
            warningBanner.text = "Đang gửi email..."

            user.sendEmailVerification()
                .addOnSuccessListener {
                    Log.d("VerificationBanner", "Gửi email xác thực thành công!")
                    Toast.makeText(this, "Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.", Toast.LENGTH_LONG).show()
                    warningBanner.text = "Đã gửi, vui lòng kiểm tra email."
                    // Không bật lại isEnabled vội để tránh spam
                }
                .addOnFailureListener { e ->
                    Log.e("VerificationBanner", "Gửi email thất bại: ${e.message}")
                    Toast.makeText(this, "Gửi lại email thất bại. Vui lòng thử lại sau.", Toast.LENGTH_SHORT).show()
                    warningBanner.isEnabled = true // Cho phép người dùng thử lại
                    warningBanner.text = "Tài khoản chưa xác thực. Nhấn để gửi lại email." // Khôi phục text gốc
                }
        }
    }

    fun navigateBasedOnRole(userId: String, navOptions: NavOptions?) {
        // 1. Khởi tạo và trỏ đến user trong Realtime Database (RTDB)
        val dbRef = FirebaseDatabase.getInstance().getReference("dataUser").child(userId)

        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val role = snapshot.child("role").getValue(String::class.java)
                    Log.d("RoleCheck", "User $userId role: $role")

                    val isManager = role == "manager"

                    setupBottomNavigation(isManager)

                    if (isManager) {
                        navController.navigate(R.id.fragment_overview, null, navOptions)
                    } else {
                        navController.navigate(R.id.fragment_home, null, navOptions)
                    }
                } else {
                    Log.e("RoleCheck", "User $userId not found in dataUser RTDB. Logging out.")
                    mAuth.signOut()
                    hideBottomNavigation()
                    navController.navigate(R.id.fragment_login)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RoleCheck", "Failed to read role from RTDB: ${error.message}")
                mAuth.signOut()
                hideBottomNavigation()
                navController.navigate(R.id.fragment_login)
            }
        })
    }


    fun setupBottomNavigation(isManager: Boolean) {
        binding.bottomNavigation.menu.clear()
        if (isManager) {
            binding.bottomNavigation.inflateMenu(R.menu.owner_menu)
        } else {
            binding.bottomNavigation.inflateMenu(R.menu.customer_menu)
        }
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)
        binding.bottomNavigation.setOnItemSelectedListener(this)
        showBottomNavigation()
    }

    fun showBottomNavigation() {
        binding.bottomNavigation.visibility = View.VISIBLE
    }

    fun hideBottomNavigation() {
        binding.bottomNavigation.visibility = View.GONE
    }

    override fun onNavigationItemSelected(p0: MenuItem): Boolean {
        return NavigationUI.onNavDestinationSelected(p0, navController)
    }

}
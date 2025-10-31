package org.o7planning.myapplication.admin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.firebase.auth.FirebaseAuth
import org.o7planning.myapplication.R
import org.o7planning.myapplication.databinding.FragmentSplashBinding

class FragmentSplash : Fragment() {
    private lateinit var binding: FragmentSplashBinding
    private lateinit var mAuth: FirebaseAuth
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSplashBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mAuth = FirebaseAuth.getInstance()

        showLoading()

        handler = Handler(Looper.getMainLooper())
        runnable = Runnable{
            hideLoading()
            checkUserStatusAndNavigate()
        }

        handler.postDelayed(runnable, 3000)

    }

    private fun completeNavigation(targetId: Int, options: NavOptions?) {
        handler.removeCallbacks(runnable)
        findNavController().navigate(targetId, null, options)
    }

    private fun checkUserStatusAndNavigate() {
        val currenUser = mAuth.currentUser

        val navOptions = navOptions {
            popUpTo(R.id.fragment_splash) {
                inclusive = true
            }
        }

        if (currenUser == null) {
            (activity as MainActivity).hideBottomNavigation()
            completeNavigation(R.id.fragment_login, navOptions)
            return
        }
        (activity as MainActivity).navigateBasedOnRole(currenUser.uid, navOptions)
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(runnable)
    }
}
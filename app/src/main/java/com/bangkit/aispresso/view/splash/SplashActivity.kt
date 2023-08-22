package com.bangkit.aispresso.view.splash

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.bangkit.aispresso.data.storage.PreferencesClass
import com.bangkit.aispresso.databinding.ActivitySplashBinding
import com.bangkit.aispresso.view.camera.CameraActivity
import com.bangkit.aispresso.view.camera.coffeprocessing.CoffeClasifyActivity
import com.bangkit.aispresso.view.dashboard.DashboardActivity
import com.bangkit.aispresso.view.onboarding.OnboardingActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, CoffeClasifyActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)
        setContentView(binding.root)

    }
}
package com.judini.cafemode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.judini.cafemode.databinding.ActivityMainBinding
import com.judini.cafemode.service.CafeModeService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        startCafeModeService()
        setupUI()
    }

    private fun startCafeModeService() {
        val serviceIntent = Intent(this, CafeModeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun setupUI() {
        val prefs = getSharedPreferences("cafemode_ui_state", MODE_PRIVATE)
        binding.toggleButton.isChecked = prefs.getBoolean("master_enabled", false)
        binding.intensitySlider.value = prefs.getFloat("intensity", 0.5f)
        binding.spatialSlider.value = prefs.getFloat("spatial_width", 0.5f)

        updateSliders(binding.toggleButton.isChecked)

        binding.toggleButton.setOnCheckedChangeListener { _, isChecked ->
            CafeModeService.isMasterEnabled = isChecked
            updateSliders(isChecked)
            sendUpdate()
        }

        binding.intensitySlider.addOnChangeListener { _, _, _ -> sendUpdate() }
        binding.spatialSlider.addOnChangeListener { _, _, _ -> sendUpdate() }
    }

    private fun updateSliders(isEnabled: Boolean) {
        binding.intensitySlider.isEnabled = isEnabled
        binding.spatialSlider.isEnabled = isEnabled
    }

    private fun sendUpdate() {
        val intent = Intent(CafeModeService.ACTION_UPDATE_SETTINGS).apply {
            putExtra("isEnabled", binding.toggleButton.isChecked)
            putExtra("intensity", binding.intensitySlider.value)
            putExtra("spatialWidth", binding.spatialSlider.value)
        }
        sendBroadcast(intent)

        getSharedPreferences("cafemode_ui_state", MODE_PRIVATE).edit().apply {
            putBoolean("master_enabled", binding.toggleButton.isChecked)
            putFloat("intensity", binding.intensitySlider.value)
            putFloat("spatial_width", binding.spatialSlider.value)
            apply()
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}
package com.kexxdumb.fbnotifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.accountsContainer)
        findViewById<TextView>(R.id.addAccountButton).setOnClickListener {
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        requestNotificationPermissionIfNeeded()
        ensureNotificationChannel(this)
        schedulePeriodicPolling(this)
    }

    override fun onResume() {
        super.onResume()
        renderProfiles()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    private fun renderProfiles() {
        container.removeAllViews()
        val profiles = ProfileStore.list(this)

        if (profiles.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.empty_accounts)
            empty.setTextColor(0xFF888888.toInt())
            container.addView(empty)
            return
        }

        val inflater = LayoutInflater.from(this)
        profiles.forEach { profile ->
            val row = inflater.inflate(R.layout.item_profile, container, false)
            row.findViewById<TextView>(R.id.profileLabel).text = profile.label
            row.setOnLongClickListener {
                confirmRemove(profile)
                true
            }
            container.addView(row)
        }
    }

    private fun confirmRemove(profile: FacebookProfile) {
        AlertDialog.Builder(this)
            .setTitle("Quitar cuenta")
            .setMessage("¿Quitar \"${profile.label}\" de las notificaciones?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Quitar") { _, _ ->
                ProfileStore.remove(this, profile.id)
                renderProfiles()
            }
            .show()
    }
}

package com.devhunts.remotehp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var addr: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val svc = Intent(this, ControlService::class.java).apply {
                    putExtra(ControlService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ControlService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, svc)
                status.text = "Status: layanan berjalan"
                refreshAddr()
            } else {
                status.text = "Status: izin rekam layar dibatalkan"
            }
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        addr = findViewById(R.id.addr)

        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ControlService::class.java))
            status.text = "Status: berhenti"
        }

        refreshAddr()
    }

    override fun onResume() {
        super.onResume()
        refreshAddr()
    }

    private fun refreshAddr() {
        val ips = localIps()
        addr.text = if (ips.isEmpty()) {
            "- (sambungkan Tailscale/Wi-Fi dulu)"
        } else {
            ips.joinToString("\n") { "http://$it:${ControlService.PORT}" }
        }
    }

    private fun localIps(): List<String> {
        val out = mutableListOf<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (a in nif.inetAddresses) {
                    if (a.isLoopbackAddress) continue
                    val host = a.hostAddress ?: continue
                    if (host.contains(':')) continue // skip IPv6
                    // Tailscale IPs (100.x) first, then LAN
                    if (host.startsWith("100.")) out.add(0, host) else out.add(host)
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}

package com.ghosttouch.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.InputDevice
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var gamepadText: TextView
    private lateinit var mouseText: TextView
    private lateinit var actionButton: Button
    private lateinit var inputManager: InputManager

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TouchBlockerService.ACTION_STATE_CHANGED) {
                refreshUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        gamepadText = findViewById(R.id.gamepadText)
        mouseText = findViewById(R.id.mouseText)
        actionButton = findViewById(R.id.actionButton)
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager

        actionButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "Primero activá el servicio de accesibilidad GhostTouch Blocker",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            val newState = !TouchBlockerService.isBlocked
            val intent = Intent(TouchBlockerService.ACTION_SET_BLOCKED)
            intent.putExtra(TouchBlockerService.EXTRA_BLOCKED, newState)
            intent.setPackage(packageName)
            sendBroadcast(intent)

            actionButton.postDelayed({ refreshUi() }, 150)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            stateReceiver,
            IntentFilter(TouchBlockerService.ACTION_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
        }
    }

    private fun refreshUi() {
        val blocked = TouchBlockerService.isBlocked
        val serviceOn = isAccessibilityServiceEnabled()

        if (!serviceOn) {
            statusText.text = "⚠️ Servicio no activado"
            actionButton.text = "ACTIVAR SERVICIO DE ACCESIBILIDAD"
        } else if (blocked) {
            statusText.text = "🔴 Táctil BLOQUEADO"
            actionButton.text = "🔓 DESBLOQUEAR TÁCTIL"
        } else {
            statusText.text = "🟢 Táctil desbloqueado"
            actionButton.text = "🔒 BLOQUEAR TÁCTIL"
        }

        gamepadText.text = if (isGamepadConnected()) "Mando Xbox: ● Detectado" else "Mando Xbox: ○ No detectado"
        mouseText.text = if (isMouseConnected()) "Mouse: ● Detectado" else "Mouse: ○ No detectado"
    }

    private fun isGamepadConnected(): Boolean {
        for (id in inputManager.inputDeviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            val sources = device.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            ) return true
        }
        return false
    }

    private fun isMouseConnected(): Boolean {
        for (id in inputManager.inputDeviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            if ((device.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) return true
        }
        return false
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${TouchBlockerService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }
}

package com.ghosttouch.blocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.hardware.input.InputManager

class TouchBlockerService : AccessibilityService() {

    companion object {
        const val TAG = "GhostTouchBlocker"
        const val ACTION_SET_BLOCKED = "com.ghosttouch.blocker.SET_BLOCKED"
        const val EXTRA_BLOCKED = "blocked"
        const val ACTION_STATE_CHANGED = "com.ghosttouch.blocker.STATE_CHANGED"

        @Volatile
        var isBlocked: Boolean = false
            private set

        @Volatile
        var instance: TouchBlockerService? = null
    }

    private var volUpPressCount = 0
    private var lastVolUpPressTime = 0L
    private val tripleClickWindowMs = 1500L
    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var blockerOverlay: View? = null
    private var inputManager: InputManager? = null

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {}

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (isBlocked && !isAnyGamepadConnected()) {
                Log.w(TAG, "Mando desconectado y sin otro gamepad activo -> desbloqueo de seguridad")
                setBlocked(false)
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {}
    }

    private fun isAnyGamepadConnected(): Boolean {
        val ids = inputManager?.inputDeviceIds ?: return false
        for (id in ids) {
            val device = inputManager?.getInputDevice(id) ?: continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (isGamepad) return true
        }
        return false
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SET_BLOCKED) {
                val blocked = intent.getBooleanExtra(EXTRA_BLOCKED, false)
                setBlocked(blocked)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(inputDeviceListener, handler)
        Log.i(TAG, "Servicio conectado")

        val filter = IntentFilter(ACTION_SET_BLOCKED)
        registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            unregisterReceiver(controlReceiver)
        } catch (e: Exception) {
        }
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        removeOverlay()
        instance = null
        return super.onUnbind(intent)
    }

    fun setBlocked(blocked: Boolean) {
        isBlocked = blocked
        Log.i(TAG, "Bloqueo táctil -> $blocked")

        if (blocked) {
            addOverlay()
        } else {
            removeOverlay()
        }

        val broadcast = Intent(ACTION_STATE_CHANGED)
        broadcast.putExtra(EXTRA_BLOCKED, blocked)
        broadcast.setPackage(packageName)
        sendBroadcast(broadcast)
    }

    private fun addOverlay() {
        if (blockerOverlay != null) return

        val overlay = View(this)
        overlay.setOnTouchListener { _, event ->
            val isTouchscreen = (event.source and InputDevice.SOURCE_TOUCHSCREEN) ==
                    InputDevice.SOURCE_TOUCHSCREEN
            isTouchscreen
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(overlay, params)
            blockerOverlay = overlay
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo agregar overlay de bloqueo: ${e.message}")
        }
    }

    private fun removeOverlay() {
        blockerOverlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        blockerOverlay = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.w(TAG, "Servicio interrumpido por el sistema")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val source = event.source
        val isFromGamepad = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

        if (isFromGamepad) {
            return false
        }

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            handleVolumeUpPress()
            return false
        }

        return false
    }

    private fun handleVolumeUpPress() {
        val now = System.currentTimeMillis()
        if (now - lastVolUpPressTime > tripleClickWindowMs) {
            volUpPressCount = 0
        }
        lastVolUpPressTime = now
        volUpPressCount++

        if (volUpPressCount >= 3) {
            volUpPressCount = 0
            if (isBlocked) {
                Log.i(TAG, "Triple Vol+ detectado -> desbloqueando táctil")
                setBlocked(false)
            }
        }
    }
}

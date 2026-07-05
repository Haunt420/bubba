package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.sqrt

class OverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var lifecycleOwner: CustomLifecycleOwner
    private lateinit var sensorManager: SensorManager

    private var isUIVisible by mutableStateOf(true)
    private var savedText: String? = null

    private var shakeTimestamp: Long = 0
    private val SHAKE_THRESHOLD_GRAVITY = 2.7f
    private val SHAKE_SLOP_TIME_MS = 500

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }

        setupComposeView()
    }

    private fun setupComposeView() {
        composeView = ComposeView(this).apply {
            setContent {
                MyApplicationTheme {
                    if (isUIVisible) {
                        FloatingUI(
                            onAppend = { handleAppend() },
                            onPrepend = { handlePrepend() },
                            onClear = { handleClear() },
                            onDrag = { dragAmount ->
                                params.x += dragAmount.x.toInt()
                                params.y += dragAmount.y.toInt()
                                windowManager.updateViewLayout(this, params)
                            }
                        )
                    }
                }
            }
        }

        lifecycleOwner = CustomLifecycleOwner()
        lifecycleOwner.start()
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(composeView, params)
        updateWindowFlags()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_VISIBILITY") {
            toggleVisibility()
        }
        return START_STICKY
    }

    private fun toggleVisibility() {
        isUIVisible = !isUIVisible
        updateWindowFlags()
    }

    private fun updateWindowFlags() {
        if (isUIVisible) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            params.width = 0
            params.height = 0
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (this::composeView.isInitialized) {
            windowManager.updateViewLayout(composeView, params)
        }
    }

    private fun handleAppend() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val newText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (newText.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        savedText = if (savedText == null) newText else savedText + newText
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipMerge", savedText))
        Toast.makeText(this, "Appended!", Toast.LENGTH_SHORT).show()
    }

    private fun handlePrepend() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val newText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (newText.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        savedText = if (savedText == null) newText else newText + savedText
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipMerge", savedText))
        Toast.makeText(this, "Prepended!", Toast.LENGTH_SHORT).show()
    }

    private fun handleClear() {
        savedText = null
        Toast.makeText(this, "ClipMerge buffer cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }
            shakeTimestamp = now
            toggleVisibility()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        lifecycleOwner.stop()
        if (this::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_channel",
                "ClipMerge Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val toggleIntent = Intent(this, OverlayService::class.java).apply {
            action = "TOGGLE_VISIBILITY"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "overlay_channel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("ClipMerge Active")
            .setContentText("Shake device to toggle UI or tap to hide/show")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setContentIntent(pendingIntent)
            .build()
    }
}

@Composable
fun FloatingUI(
    onAppend: () -> Unit,
    onPrepend: () -> Unit,
    onClear: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    Column(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CustomFab(
            text = "PREPEND NEXT",
            icon = Icons.Filled.KeyboardArrowUp,
            accentColor = Color(0xFFA855F7), // Purple 500
            lightAccentColor = Color(0xFFD8B4FE), // Purple 300
            glowColor = Color(0x33A855F7), // Purple 500 20%
            onClick = onPrepend,
            onLongClick = onClear
        )
        CustomFab(
            text = "APPEND NEXT",
            icon = Icons.Filled.KeyboardArrowDown,
            accentColor = Color(0xFF84CC16), // Lime 500
            lightAccentColor = Color(0xFFBEF264), // Lime 300
            glowColor = Color(0x3384CC16), // Lime 500 20%
            onClick = onAppend,
            onLongClick = onClear
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomFab(
    text: String,
    icon: ImageVector,
    accentColor: Color,
    lightAccentColor: Color,
    glowColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(Color(0x66000000), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                color = lightAccentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(20.dp, RoundedCornerShape(24.dp), spotColor = accentColor.copy(alpha = 0.3f), ambientColor = accentColor.copy(alpha = 0.3f))
                .background(Color(0x99000000), RoundedCornerShape(24.dp))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(glowColor, CircleShape)
                    .blur(12.dp)
            )
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

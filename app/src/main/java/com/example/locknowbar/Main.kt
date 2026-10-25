package com.example.locknowbar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Lock NowBar", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("1) السماح بقراءة الإشعارات") }
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("2) السماح بإظهار الإشعارات") }
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 34) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    }) { Text("3) السماح بالعرض فوق شاشة القفل") }
                    Button(onClick = {
                        NotifStore.add(Notif("test", "تجربة", "مرحباً", "هذا إشعار تجريبي"))
                        startActivity(Intent(this@MainActivity, LockActivity::class.java))
                    }) { Text("4) تجربة") }
                }
            }
        }
    }
}

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent { LockScreen(onClose = { finish() }) }
    }
}

@Composable
fun LockScreen(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F0F1A), Color(0xFF303164))))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NotifStore.items.take(4).forEach { n ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3D3E7A), RoundedCornerShape(32.dp))
                        .clickable { NotifStore.items.remove(n) }
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Text(n.app, color = Color(0xFFB9BAFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(n.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(n.text, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

package com.example.nowbrief

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

val listenerEnabled = mutableStateOf(false)

data class Ev(val title: String, val begin: Long, val allDay: Boolean)

data class Match(
    val home: String,
    val away: String,
    val homeScore: String?,
    val awayScore: String?,
    val key: String,
    val whenText: String
)

fun loadEvents(ctx: Context): List<Ev> {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR)
        != PackageManager.PERMISSION_GRANTED
    ) return emptyList()
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val begin = c.timeInMillis
    val end = begin + 24L * 60 * 60 * 1000
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uri, begin)
    ContentUris.appendId(uri, end)
    val out = mutableListOf<Ev>()
    try {
        ctx.contentResolver.query(
            uri.build(),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY
            ),
            null, null,
            CalendarContract.Instances.BEGIN + " ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                out.add(Ev(cur.getString(0) ?: "", cur.getLong(1), cur.getInt(2) == 1))
            }
        }
    } catch (e: Exception) {
    }
    return out
}

fun batteryInfo(ctx: Context): Pair<Int, Boolean> {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
    return Pair(pct, charging)
}

fun weatherText(code: Int): String = when (code) {
    0 -> "صافٍ"
    1, 2 -> "غائم جزئياً"
    3 -> "غائم"
    45, 48 -> "ضباب"
    in 51..57 -> "رذاذ"
    in 61..67 -> "مطر"
    in 71..77 -> "ثلج"
    in 80..82 -> "زخات مطر"
    in 95..99 -> "عاصفة رعدية"
    else -> "—"
}

fun getJson(url: String): JSONObject {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000
    c.readTimeout = 10000
    return try {
        JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } finally {
        c.disconnect()
    }
}

fun fetchWeather(ctx: Context, done: (String) -> Unit) {
    Thread {
        val result = try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                "اسمح بالموقع لعرض الطقس"
            } else {
                val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = lm.getProviders(true)
                    .mapNotNull { lm.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }
                if (loc == null) {
                    "الموقع غير متاح بعد"
                } else {
                    val j = getJson(
                        "https://api.open-meteo.com/v1/forecast?latitude=" + loc.latitude +
                            "&longitude=" + loc.longitude +
                            "&current=temperature_2m,weather_code"
                    )
                    val cur = j.getJSONObject("current")
                    cur.getDouble("temperature_2m").roundToInt().toString() + "° — " +
                        weatherText(cur.getInt("weather_code"))
                }
            }
        } catch (e: Exception) {
            "تعذّر جلب الطقس"
        }
        Handler(Looper.getMainLooper()).post { done(result) }
    }.start()
}

fun localTime(ts: String, date: String): String {
    if (ts.length >= 19) {
        try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            p.timeZone = TimeZone.getTimeZone("UTC")
            val d = p.parse(ts.substring(0, 19))
            if (d != null) {
                return SimpleDateFormat("d MMM  HH:mm", Locale.getDefault()).format(d)
            }
        } catch (e: Exception) {
        }
    }
    return date
}

fun strOrNull(o: JSONObject, name: String): String? =
    if (o.isNull(name)) null else o.optString(name)

fun parseMatches(j: JSONObject): List<Match> {
    val arr = j.optJSONArray("events") ?: return emptyList()
    val out = mutableListOf<Match>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val ts = strOrNull(o, "strTimestamp") ?: ""
        val date = strOrNull(o, "dateEvent") ?: ""
        out.add(
            Match(
                home = strOrNull(o, "strHomeTeam") ?: "",
                away = strOrNull(o, "strAwayTeam") ?: "",
                homeScore = strOrNull(o, "intHomeScore"),
                awayScore = strOrNull(o, "intAwayScore"),
                key = if (ts.isNotEmpty()) ts else date,
                whenText = localTime(ts, date)
            )
        )
    }
    return out
}

fun fetchMatches(leagueId: String, done: (List<Match>, List<Match>, String?) -> Unit) {
    Thread {
        var p: List<Match> = emptyList()
        var n: List<Match> = emptyList()
        var err: String? = null
        try {
            val base = "https://www.thesportsdb.com/api/v1/json/3/"
            p = parseMatches(getJson(base + "eventspastleague.php?id=" + leagueId))
                .sortedByDescending { it.key }.take(6)
            n = parseMatches(getJson(base + "eventsnextleague.php?id=" + leagueId))
                .sortedBy { it.key }.take(4)
            if (p.isEmpty() && n.isEmpty()) err = "لا توجد مباريات متاحة"
        } catch (e: Exception) {
            err = "تعذّر جلب المباريات"
        }
        val fp = p
        val fn = n
        val fe = err
        Handler(Looper.getMainLooper()).post { done(fp, fn, fe) }
    }.start()
}

fun loadNotes(ctx: Context): List<String> {
    val s = ctx.getSharedPreferences("brief", Context.MODE_PRIVATE).getString("notes", "") ?: ""
    return s.split("\n").filter { it.isNotBlank() }
}

fun saveNotes(ctx: Context, list: List<String>) {
    ctx.getSharedPreferences("brief", Context.MODE_PRIVATE)
        .edit().putString("notes", list.joinToString("\n")).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { BriefScreen() } }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabled.value =
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }
}

@Composable
fun BriefCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x33FFFFFF), RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Color(0xCCFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun MatchRow(m: Match) {
    val hs = m.homeScore
    val aws = m.awayScore
    val played = hs != null && aws != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            m.home, color = Color.White, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
        )
        Column(
            Modifier.weight(1.1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (played) {
                Text(
                    hs + " - " + aws, color = Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(m.whenText, color = Color(0x99FFFFFF), fontSize = 11.sp)
            } else {
                Text(
                    m.whenText, color = Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            m.away, color = Color.White, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun BriefScreen() {
    val ctx = LocalContext.current
    var events by remember { mutableStateOf(loadEvents(ctx)) }
    var weather by remember { mutableStateOf("جارٍ التحميل…") }
    val battery = remember { batteryInfo(ctx) }
    val notes = remember { mutableStateListOf<String>().apply { addAll(loadNotes(ctx)) } }
    var draft by remember { mutableStateOf("") }

    val leagues = listOf(
        "الإنجليزي" to "4328",
        "الإسباني" to "4335",
        "الإيطالي" to "4332",
        "الألماني" to "4331",
        "الفرنسي" to "4334",
        "أبطال أوروبا" to "4480",
        "السعودي" to "4668"
    )
    var leagueId by remember { mutableStateOf("4328") }
    var past by remember { mutableStateOf<List<Match>>(emptyList()) }
    var next by remember { mutableStateOf<List<Match>>(emptyList()) }
    var footMsg by remember { mutableStateOf("جارٍ التحميل…") }
    var tick by remember { mutableStateOf(0) }
    val chips = rememberScrollState()
    val listenerOn by listenerEnabled

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        events = loadEvents(ctx)
        fetchWeather(ctx) { weather = it }
    }
    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    LaunchedEffect(leagueId, tick) {
        footMsg = "جارٍ التحميل…"
        fetchMatches(leagueId) { p, n, err ->
            past = p
            next = n
            footMsg = err ?: ""
        }
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val title: String
    val greet: String
    val colors: List<Color>
    when {
        hour in 5..11 -> {
            title = "ملخص الصباح"; greet = "صباح الخير"
            colors = listOf(Color(0xFF4FA3E0), Color(0xFF1B4F8A))
        }
        hour in 12..17 -> {
            title = "ملخص اليوم"; greet = "نهارك سعيد"
            colors = listOf(Color(0xFFE8943A), Color(0xFF7A3B6E))
        }
        else -> {
            title = "ملخص المساء"; greet = "مساء الخير"
            colors = listOf(Color(0xFF2B2B5E), Color(0xFF0A0A1A))
        }
    }
    val dateText = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(greet, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(dateText, color = Color(0xCCFFFFFF), fontSize = 16.sp)
            Text(
                "لديك " + events.size + " موعد اليوم",
                color = Color.White, fontSize = 16.sp
            )

            BriefCard("الطقس") {
                Text(weather, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            BriefCard("المواعيد") {
                if (events.isEmpty()) {
                    Text("لا توجد مواعيد اليوم", color = Color.White, fontSize = 16.sp)
                } else {
                    events.forEach { e ->
                        val t = if (e.allDay) "طوال اليوم" else timeFmt.format(Date(e.begin))
                        Text(t + "   " + e.title, color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            BriefCard("كرة القدم") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(chips),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    leagues.forEach { pair ->
                        val selected = pair.second == leagueId
                        Text(
                            pair.first, color = Color.White, fontSize = 14.sp,
                            modifier = Modifier
                                .background(
                                    if (selected) Color(0x66FFFFFF) else Color(0x22FFFFFF),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { leagueId = pair.second }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                if (footMsg.isNotEmpty()) {
                    Text(footMsg, color = Color(0xCCFFFFFF), fontSize = 14.sp)
                }
                if (past.isNotEmpty()) {
                    Text("آخر النتائج", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                    past.forEach { MatchRow(it) }
                }
                if (next.isNotEmpty()) {
                    Text("المباريات القادمة", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                    next.forEach { MatchRow(it) }
                }
                Text(
                    "↻ تحديث", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.clickable { tick++ }.padding(vertical = 4.dp)
                )
            }

            BriefCard("الإشعارات") {
                if (!listenerOn) {
                    Text("فعّل الإذن لعرض إشعاراتك هنا", color = Color.White, fontSize = 15.sp)
                    Text(
                        "تفعيل", color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0x44FFFFFF), RoundedCornerShape(16.dp))
                            .clickable {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else if (NotifStore.items.isEmpty()) {
                    Text("لا توجد إشعارات", color = Color.White, fontSize = 15.sp)
                } else {
                    NotifStore.items.take(5).forEach { n ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { NotifStore.items.remove(n) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                n.app, color = Color(0xCCFFFFFF), fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                n.title, color = Color.White, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                n.text, color = Color.White, fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            BriefCard("الهاتف") {
                val b = if (battery.first >= 0) battery.first.toString() + "%" else "—"
                val c = if (battery.second) "  (يشحن)" else ""
                Text("البطارية: " + b + c, color = Color.White, fontSize = 16.sp)
            }

            BriefCard("تذكيراتي") {
                notes.toList().forEach { n ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(n, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            "✓", color = Color.White, fontSize = 22.sp,
                            modifier = Modifier
                                .clickable {
                                    notes.remove(n)
                                    saveNotes(ctx, notes)
                                }
                                .padding(8.dp)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.replace("\n", " ") },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text("أضف تذكيراً…", color = Color(0x99FFFFFF), fontSize = 16.sp)
                                }
                                inner()
                            }
                        }
                    )
                    Text(
                        "+", color = Color.White, fontSize = 26.sp,
                        modifier = Modifier
                            .clickable {
                                if (draft.isNotBlank()) {
                                    notes.add(0, draft.trim())
                                    saveNotes(ctx, notes)
                                    draft = ""
                                }
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

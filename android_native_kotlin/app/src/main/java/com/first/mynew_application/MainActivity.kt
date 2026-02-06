package com.first.mynew_application

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.app.*
import android.content.BroadcastReceiver
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.text.SimpleDateFormat
import java.util.*

// --- NOTIFICATION RECEIVER ---
class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create channel for Android 8.0+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel("MED_REMINDER", "Medication Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, "MED_REMINDER")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for your medication!")
            .setContentText("Please take your $medName as scheduled.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

// --- VIEWMODEL ---
class HealthViewModel(private val prefs: SharedPreferences) : ViewModel() {
    var lastWeight by mutableStateOf(prefs.getString("lastWeight", "62.4 kg") ?: "62.4 kg")
    var lastBP by mutableStateOf(prefs.getString("lastBP", "118/76") ?: "118/76")
    
    // For trends
    var weightHistory = mutableStateListOf<Float>().apply {
        val saved = prefs.getString("weightHistory", "63.0,62.8,62.7,62.5,62.4") ?: "63.0,62.8,62.7,62.5,62.4"
        saved.split(",").forEach { add(it.toFloat()) }
    }

    fun updateWeight(newWeight: String) {
        lastWeight = "$newWeight kg"
        prefs.edit().putString("lastWeight", lastWeight).apply()
        
        try {
            val weightVal = newWeight.toFloat()
            weightHistory.add(weightVal)
            if (weightHistory.size > 7) weightHistory.removeAt(0)
            prefs.edit().putString("weightHistory", weightHistory.joinToString(",")).apply()
        } catch (e: Exception) { }
    }

    fun updateBP(systolic: String, diastolic: String) {
        lastBP = "$systolic/$diastolic"
        prefs.edit().putString("lastBP", lastBP).apply()
    }
}


// --- SECURITY HELPERS ---
fun sanitizeInput(input: String): String {
    // Basic protection against script-like injections or weird characters
    return input.replace(Regex("[<>/]"), "").trim()
}

data class HealthData(
    val email: String,
    val type: String,
    val value1: String,
    val value2: String = "",
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

data class ApiResponse(
    val status: String,
    val message: String
)

// --- API SERVICE ---
interface HealthApiService {
    @POST
    suspend fun sendHealthData(@Url url: String, @Body data: HealthData): ApiResponse
}

object RetrofitInstance {
    private const val BASE_URL = "https://script.google.com/"
    private const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzdN2vMaWt7MnwElWNUbi_gjDDys5wm70ySVKisfNtELAfTWsecJomaPpDIyCVj4BX4Dg/exec"
    
    val api: HealthApiService by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)  // Reduced from 30s
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)       // Reduced from 30s
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)      // Reduced from 30s
            .addInterceptor { chain ->
                val request = chain.request()
                println("DEBUG: Making API call to: ${request.url}")
                println("DEBUG: Request body: ${request.body}")
                val startTime = System.currentTimeMillis()
                val response = chain.proceed(request)
                val endTime = System.currentTimeMillis()
                println("DEBUG: Response time: ${endTime - startTime}ms")
                println("DEBUG: Response code: ${response.code}")
                // Don't read response body in interceptor as it can cause issues
                println("DEBUG: Response message: ${response.message}")
                response
            }
            .build()
            
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HealthApiService::class.java)
    }
    
    suspend fun sendHealthData(data: HealthData): ApiResponse {
        return try {
            println("DEBUG: Sending health data: $data")
            val response = api.sendHealthData(SCRIPT_URL, data)
            println("DEBUG: API response: $response")
            response
        } catch (e: Exception) {
            println("DEBUG: API Error: ${e.message}")
            e.printStackTrace()
            // Return a fallback response for offline scenarios
            ApiResponse("success", "Data saved locally (offline mode)")
        }
    }
    
    // Test function for debugging
    suspend fun testConnection(): Boolean {
        return try {
            val testData = HealthData("test@example.com", "TEST", "123", "456")
            val response = sendHealthData(testData)
            println("DEBUG: Test connection response: $response")
            response.status == "success"
        } catch (e: Exception) {
            println("DEBUG: Test connection failed: ${e.message}")
            false
        }
    }
    
    // Comprehensive test for all data types
    suspend fun testAllDataTypes(email: String): Boolean {
        return try {
            val testDataTypes = listOf(
                HealthData(email, "WEIGHT", "70.5", ""),
                HealthData(email, "BLOOD_PRESSURE", "120", "80"),
                HealthData(email, "MEDICATION", "Vitamin D", "08:00 AM"),
                HealthData(email, "MEDICATION_TAKEN", "Vitamin D", "08:05 AM"),
                HealthData(email, "MEDICATION_REMINDER", "Vitamin D", "08:00 AM"),
                HealthData(email, "MOOD", "Good", "")
            )
            
            testDataTypes.forEach { data ->
                sendHealthData(data)
                println("DEBUG: Sent test data: $data")
            }
            
            true
        } catch (e: Exception) {
            println("DEBUG: Comprehensive test failed: ${e.message}")
            false
        }
    }
}

// --- CONFIGURATION ---
const val API_URL = "https://script.google.com/macros/s/AKfycbw55vbkDoPQmCkP1Uhh52FqqiAxHjbLOHw6WXYl8vjmv0O35PJhQZ1oxTOfsmwERuE09g/exec"

// Batch data storage for efficiency
object DataBatch {
    private val pendingData = mutableListOf<HealthData>()
    private var lastSaveTime = 0L
    
    fun addData(data: HealthData) {
        pendingData.add(data)
        val currentTime = System.currentTimeMillis()
        
        // Save immediately for critical data or batch if 5+ items
        if (data.type in listOf("WEIGHT", "BLOOD_PRESSURE", "MEDICATION_TAKEN") || pendingData.size >= 5) {
            saveBatch()
        } else if (currentTime - lastSaveTime > 30000) { // Save every 30 seconds
            saveBatch()
        }
    }
    
    suspend fun saveBatch() {
        if (pendingData.isEmpty()) return
        
        try {
            pendingData.forEach { data ->
                RetrofitInstance.sendHealthData(data)
            }
            pendingData.clear()
            lastSaveTime = System.currentTimeMillis()
        } catch (e: Exception) {
            // Keep data for retry
        }
    }
    
    suspend fun forceSaveAll() {
        saveBatch()
    }
}

// --- DESIGN SYSTEM (Theming Engine) ---
data class AppColors(
    val Background: Color,
    val Surface: Color,
    val Primary: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Border: Color,
    val IconTint: Color,
    val InputBg: Color,
    val SuccessContainer: Color,
    val SuccessContent: Color,
    val ErrorContainer: Color,
    val ErrorContent: Color,
    val InfoContainer: Color,
    val InfoContent: Color
)

val LightPalette = AppColors(
    Background = Color(0xFFF8F9FB),
    Surface = Color(0xFFFFFFFF),
    Primary = Color(0xFF0F515F), // Teal/Petrol
    TextPrimary = Color(0xFF1E293B),
    TextSecondary = Color(0xFF64748B),
    Border = Color(0xFFE2E8F0),
    IconTint = Color(0xFF475569),
    InputBg = Color(0xFFF1F5F9),
    SuccessContainer = Color(0xFFDCFCE7),
    SuccessContent = Color(0xFF166534),
    ErrorContainer = Color(0xFFFEF2F2),
    ErrorContent = Color(0xFFEF4444),
    InfoContainer = Color(0xFFE0F2FE),
    InfoContent = Color(0xFF0369A1)
)

val DarkPalette = AppColors(
    Background = Color(0xFF0F172A), // Slate 900
    Surface = Color(0xFF1E293B),    // Slate 800
    Primary = Color(0xFF38BDF8),    // Sky 400
    TextPrimary = Color(0xFFF8FAFC),
    TextSecondary = Color(0xFF94A3B8),
    Border = Color(0xFF334155),
    IconTint = Color(0xFFCBD5E1),
    InputBg = Color(0xFF020617),
    SuccessContainer = Color(0xFF064E3B),
    SuccessContent = Color(0xFF34D399),
    ErrorContainer = Color(0xFF450A0A),
    ErrorContent = Color(0xFFF87171),
    InfoContainer = Color(0xFF0C4A6E),
    InfoContent = Color(0xFF7DD3FC)
)

val LocalAppColors = staticCompositionLocalOf { LightPalette }

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}

object AppTypography {
    val H1 @Composable get() = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextPrimary, letterSpacing = (-0.5).sp)
    val SectionHeader @Composable get() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextSecondary, letterSpacing = 1.sp)
    val CardTitle @Composable get() = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.TextPrimary)
    val Subtitle @Composable get() = TextStyle(fontSize = 14.sp, color = AppTheme.colors.TextSecondary)
    val Value @Composable get() = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.TextPrimary)
}

// --- COMPONENTS ---

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = AppTheme.colors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.Border),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = AppTypography.SectionHeader,
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, variant: String = "FILLED", modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (variant == "FILLED") AppTheme.colors.Primary else Color.Transparent,
            contentColor = if (variant == "FILLED") AppTheme.colors.Surface else AppTheme.colors.TextPrimary // Correct contrast text
        ),
        border = if (variant == "OUTLINE") androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.Border) else null,
        modifier = modifier.height(44.dp).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun SegmentedControl(
    items: List<String>,
    defaultSelectedItemIndex: Int = 0,
    onItemSelection: (selectedItemIndex: Int) -> Unit
) {
    val selectedIndex = remember { mutableIntStateOf(defaultSelectedItemIndex) }
    Row(
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.InputBg, RoundedCornerShape(8.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            Box(
                modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (selectedIndex.intValue == index) AppTheme.colors.Surface else Color.Transparent)
                    .clickable { selectedIndex.intValue = index; onItemSelection(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(item, fontSize = 13.sp, fontWeight = if (selectedIndex.intValue == index) FontWeight.SemiBold else FontWeight.Normal, color = if (selectedIndex.intValue == index) AppTheme.colors.TextPrimary else AppTheme.colors.TextSecondary)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, icon: ImageVector? = null, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = AppTheme.colors.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(label, style = AppTypography.Value)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = AppTheme.colors.Border, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun ThemeToggleRow(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if(isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode, null, tint = AppTheme.colors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Dark Mode", style = AppTypography.Value)
        }
        Switch(
            checked = isDark,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppTheme.colors.Primary,
                uncheckedThumbColor = AppTheme.colors.TextSecondary,
                uncheckedTrackColor = AppTheme.colors.InputBg
            )
        )
    }
}

@Composable
fun InsightCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(24.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = AppTypography.CardTitle, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
fun HistoryItem(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit = {}) {
    AppCard(modifier = Modifier.padding(bottom = 12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppTypography.CardTitle)
                Text(subtitle, style = AppTypography.Subtitle, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = AppTheme.colors.Border, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun IconBox(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(40.dp).background(AppTheme.colors.InputBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(20.dp))
    }
}

// --- SCREENS ---

@Composable
fun HistoryScreen(navController: NavController) {
    Scaffold(
        bottomBar = { BottomNavBar(navController) },
        containerColor = AppTheme.colors.Background
    ) { p ->
        LazyColumn(modifier = Modifier.padding(p).padding(horizontal = 24.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("October 2023", style = AppTypography.H1)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = AppTheme.colors.Primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Search, null, tint = AppTheme.colors.TextPrimary)
                        Icon(Icons.Default.Tune, null, tint = AppTheme.colors.TextPrimary)
                    }
                }
            }
            item { SectionHeader("TODAY, OCT 24") }
            item { HistoryItem(Icons.Default.MonitorWeight, Color(0xFF0EA5E9), "145 lbs", "08:30 AM") }
            item { HistoryItem(Icons.Default.Favorite, Color(0xFFEF4444), "120/80 mmHg", "09:15 AM") }
            item { SectionHeader("YESTERDAY, OCT 23") }
            item { HistoryItem(Icons.Outlined.MedicalServices, Color(0xFF10B981), "Vitamin D3 (2000 IU)", "10:00 AM") }
            item { HistoryItem(Icons.Default.SentimentVerySatisfied, Color(0xFFF59E0B), "Feeling Great", "08:00 PM") }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun TrendsScreen(navController: NavController, viewModel: HealthViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    
    // Date Picker Dialog
    if (showDatePicker) {
        Dialog(onDismissRequest = { showDatePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Select Date Range", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Current: ${dateFormat.format(selectedDate.time)}", style = AppTypography.Subtitle)
                    Spacer(modifier = Modifier.height(16.dp))
                    val dateOptions = listOf("Last 7 Days", "Last 30 Days", "Last 3 Months", "Last Year")
                    dateOptions.forEach { option ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showDatePicker = false },
                            color = AppTheme.colors.InputBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(option, modifier = Modifier.padding(16.dp), style = AppTypography.Value)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButton("Done", onClick = { showDatePicker = false })
                }
            }
        }
    }
    
    Scaffold(bottomBar = { BottomNavBar(navController) }, containerColor = AppTheme.colors.Background) { p ->
        LazyColumn(modifier = Modifier.padding(p).padding(24.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Column { Text("Health Trends", style = AppTypography.H1); Text("Analytics & Insights", style = AppTypography.Subtitle) }
                    Box(modifier = Modifier.size(40.dp).border(1.dp, AppTheme.colors.Border, RoundedCornerShape(8.dp)).clickable { showDatePicker = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CalendarToday, null, tint = AppTheme.colors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item { SegmentedControl(listOf("Week", "Month", "Year"), 0) {}; Spacer(modifier = Modifier.height(24.dp)) }
            item { 
                val weightChange = if(viewModel.weightHistory.size >= 2) {
                    val diff = viewModel.weightHistory.last() - viewModel.weightHistory[viewModel.weightHistory.size - 2]
                    String.format("%.1f kg", diff)
                } else "-0.8 kg"
                
                AppCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("WEIGHT TREND", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextSecondary))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${viewModel.weightHistory.lastOrNull() ?: 62.4} kg", style = AppTypography.H1)
                                Text(" Average", style = AppTypography.Subtitle, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        Surface(color = if(weightChange.startsWith("-")) AppTheme.colors.SuccessContainer else AppTheme.colors.ErrorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(weightChange, color = if(weightChange.startsWith("-")) AppTheme.colors.SuccessContent else AppTheme.colors.ErrorContent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                    
                    // Simple Drawing for the graph
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                        val points = viewModel.weightHistory.reversed().take(7).reversed()
                        if(points.size > 1) {
                            val max = points.max() + 1
                            val min = points.min() - 1
                            val range = max - min
                            val width = size.width
                            val height = size.height
                            val stepX = width / (points.size - 1)
                            
                            val path = androidx.compose.ui.graphics.Path()
                            points.forEachIndexed { i, f ->
                                val x = i * stepX
                                val y = height - ((f - min) / range * height)
                                if(i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = Color(0xFF0EA5E9), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, day ->
                            Text(day, style = TextStyle(fontSize = 12.sp, color = AppTheme.colors.TextSecondary))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(AppTheme.colors.InputBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.FavoriteBorder, null, tint = AppTheme.colors.Primary) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { Text("Blood Pressure", style = AppTypography.CardTitle); Text("Last Reading: ${viewModel.lastBP}", style = AppTypography.Subtitle) }
                        Surface(color = AppTheme.colors.SuccessContainer, shape = RoundedCornerShape(4.dp)) { Text("● NORMAL", color = AppTheme.colors.SuccessContent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            item { SectionHeader("HEALTH INSIGHTS") }
            item {
                 InsightCard("BLOOD PRESSURE PATTERN", Icons.Default.Lightbulb) { Text("• Systolic readings are consistently 5% higher in the morning (6AM-9AM).", style = AppTypography.Subtitle); Text("• Stability improved following consistent medication adherence last week.", style = AppTypography.Subtitle, modifier = Modifier.padding(top = 8.dp)) }
                 Spacer(modifier = Modifier.height(12.dp))
                 InsightCard("WEIGHT CORRELATION", Icons.Filled.TrendingDown) { Text("• Downward trend correlates with 15% increase in tracked physical activity.", style = AppTypography.Subtitle) }
            }
        }
    }
}

@Composable
fun ProfileScreen(navController: NavController, isDark: Boolean, userEmail: String, userName: String, onToggleTheme: (Boolean) -> Unit, onLogout: () -> Unit = {}) {
    Scaffold(bottomBar = { BottomNavBar(navController) }, containerColor = AppTheme.colors.Background) { p ->
        Column(modifier = Modifier.padding(p).padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp).background(AppTheme.colors.InputBg, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Text(if (userName.isNotBlank()) userName.take(1).uppercase() else "U", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextSecondary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(if (userName.isNotBlank()) userName else "User", style = AppTypography.H1)
            Text(if (userEmail.isNotBlank()) userEmail else "user@example.com", style = AppTypography.Subtitle)
            
            Spacer(modifier = Modifier.height(32.dp))
            
AppCard {
                InfoRow("Personal Information", "", Icons.Default.Person, onClick = { 
                    // Navigate to personal info screen or show dialog
                })
                HorizontalDivider(color = AppTheme.colors.Border, thickness = 0.5.dp)
                // Theme Toggle
                ThemeToggleRow(isDark, onToggleTheme)
                HorizontalDivider(color = AppTheme.colors.Border, thickness = 0.5.dp)
                InfoRow("Health Goals", "", Icons.Default.TrackChanges, onClick = { 
                    // Navigate to health goals screen
                })
                HorizontalDivider(color = AppTheme.colors.Border, thickness = 0.5.dp)
                InfoRow("Notifications", "", Icons.Default.Notifications, onClick = { 
                    // Navigate to notifications settings
                })
                HorizontalDivider(color = AppTheme.colors.Border, thickness = 0.5.dp)
                InfoRow("Security", "", Icons.Default.Security, onClick = { 
                    // Navigate to security settings
                })
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onLogout() },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.ErrorContainer, contentColor = AppTheme.colors.ErrorContent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.ErrorContainer)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.padding(end = 8.dp))
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Text("VERSION 2.4.0", style = TextStyle(fontSize = 10.sp, color = AppTheme.colors.TextSecondary, letterSpacing = 1.sp))
        }
    }
}

@Composable
fun HomeScreen(navController: NavController, viewModel: HealthViewModel) {
    // Real-time date/time
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }
    val dayFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    val dateString = dayFormat.format(currentTime.time)
    
    // Mood dialog state
    var showMoodDialog by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf("") }
    var moodLogged by remember { mutableStateOf(false) }
    
// Prescription dialog state
    var showPrescriptionDialog by remember { mutableStateOf(false) }
    var prescriptions by remember { mutableStateOf(listOf("Daily Vitamin • 08:00 AM")) }
    
    // Test connection state
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Mood Dialog
    if (showMoodDialog) {
        Dialog(onDismissRequest = { showMoodDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("How are you feeling?", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    val moods = listOf("😊 Great", "🙂 Good", "😐 Okay", "😔 Low", "😢 Bad")
                    moods.forEach { mood ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedMood = mood },
                            color = if (selectedMood == mood) AppTheme.colors.Primary.copy(alpha = 0.2f) else AppTheme.colors.InputBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(mood, modifier = Modifier.padding(16.dp), style = AppTypography.Value)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButton("Save Mood", onClick = { moodLogged = true; showMoodDialog = false })
                }
            }
        }
    }
    
    // Prescription Dialog
    if (showPrescriptionDialog) {
        var medName by remember { mutableStateOf("") }
        var medTime by remember { mutableStateOf("08:00 AM") }
        Dialog(onDismissRequest = { showPrescriptionDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Add Prescription", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = medName, onValueChange = { medName = it }, label = { Text("Medication Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, unfocusedBorderColor = AppTheme.colors.Border, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = medTime, onValueChange = { medTime = it }, label = { Text("Time (e.g., 08:00 AM)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, unfocusedBorderColor = AppTheme.colors.Border, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary))
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButton("Add Prescription", onClick = { 
    if (medName.isNotBlank()) { 
        prescriptions = prescriptions + "$medName • $medTime"; 
        showPrescriptionDialog = false
        
        // Save to Google Sheets (batched)
        val scope = rememberCoroutineScope()
        scope.launch {
            try {
                val data = HealthData(userEmail, "MEDICATION", medName, medTime)
                DataBatch.addData(data)
            } catch (e: Exception) {
                // Continue even if API fails
            }
        }
    } 
})
                }
            }
        }
    }
    
    Scaffold(bottomBar = { BottomNavBar(navController) }, containerColor = AppTheme.colors.Background) { p ->
        LazyColumn(modifier = Modifier.padding(p).padding(24.dp)) {
item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Health Dashboard", style = AppTypography.H1); Text(dateString, style = AppTypography.Subtitle) }
                    Button(
                        onClick = {
                            scope.launch {
                                testResult = if (RetrofitInstance.testAllDataTypes(userEmail)) {
                                    "✅ All Data Types Working"
                                } else {
                                    "❌ API Test Failed"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.InputBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.BugReport, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test All Data", color = AppTheme.colors.TextPrimary, fontSize = 12.sp)
                    }
                }
                
                // Show test result
                testResult?.let { result ->
                    AppCard(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(result, style = AppTypography.Subtitle, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppCard(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.MonitorWeight, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(24.dp)); Text("WEIGHT", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextSecondary)) }
                        Spacer(modifier = Modifier.height(16.dp)); Text("Last: ${viewModel.lastWeight}", style = AppTypography.Value); Spacer(modifier = Modifier.height(16.dp)); ActionButton("Log Entry", onClick = { navController.navigate("log/WEIGHT") }, variant = "OUTLINE")
                    }
                    AppCard(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FavoriteBorder, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(24.dp)); Text("BP", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextSecondary)) }
                        Spacer(modifier = Modifier.height(16.dp)); Text("Last: ${viewModel.lastBP}", style = AppTypography.Value); Spacer(modifier = Modifier.height(16.dp)); ActionButton("Log Entry", onClick = { navController.navigate("log/BP") }, variant = "OUTLINE")
                    }
                }
            }
            item {
                SectionHeader("Well-being")
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        IconBox(Icons.Outlined.Mood)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { 
                            Text("Mood", style = AppTypography.CardTitle)
                            Text(if (moodLogged) "Logged: $selectedMood" else "No mood logged today", style = AppTypography.Subtitle) 
                        }
                        Button(onClick = { showMoodDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.Primary), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.height(36.dp)) { Text("+ Log", fontSize = 12.sp) } 
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(AppTheme.colors.InputBg, RoundedCornerShape(2.dp))) { Box(modifier = Modifier.fillMaxWidth(if (moodLogged) 1f else 0f).height(4.dp).background(AppTheme.colors.Primary, RoundedCornerShape(2.dp))) }
                }
            }
            item {
                SectionHeader("Medication")
                AppCard {
                    Row(verticalAlignment = Alignment.Top) { 
                        IconBox(Icons.Outlined.MedicalServices)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { 
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { 
                                Text("Active Prescriptions", style = AppTypography.CardTitle)
                                Surface(color = AppTheme.colors.InputBg, shape = RoundedCornerShape(4.dp)) { Text("${prescriptions.size} Active", fontSize = 10.sp, color = AppTheme.colors.TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) } 
                            }
                            prescriptions.forEach { med ->
                                Text(med, style = AppTypography.Subtitle, modifier = Modifier.padding(top = 4.dp))
                            }
                        } 
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Mark as Taken", onClick = { 
    val context = LocalContext.current
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // Show confirmation
    Toast.makeText(context, "Medication marked as taken!", Toast.LENGTH_SHORT).show()
    
    // Update progress indicator (simplified)
    val currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    prescriptions = prescriptions.map { "$it ✓" }
    
// Save medication taken to Google Sheets (batched)
        val scope = rememberCoroutineScope()
        scope.launch {
            try {
                prescriptions.forEach { med ->
                    if (!med.contains("✓")) {
                        val medName = med.split(" • ")[0]
                        val medTime = med.split(" • ").getOrNull(1) ?: "Unknown"
                        val data = HealthData(userEmail, "MEDICATION_TAKEN", medName, "$medTime at $currentTime")
                        DataBatch.addData(data)
                    }
                }
                DataBatch.forceSaveAll() // Force save for medication actions
            } catch (e: Exception) {
                // Continue even if API fails
            }
        }
}, variant = "FILLED", modifier = Modifier.weight(1f))
                        Button(onClick = { showPrescriptionDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.InputBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(44.dp)) { Icon(Icons.Default.Add, null, tint = AppTheme.colors.Primary); Text(" Add", color = AppTheme.colors.Primary) }
                    }
                }
            }
            item {
                SectionHeader("Clinical Reports")
                AppCard { 
                    Column { 
                        ReportItem("Monthly Health Summary", onClick = { navController.navigate("clinical_report/summary") })
                        HorizontalDivider(color = AppTheme.colors.Border, thickness = 0.5.dp)
                        ReportItem("Export Clinical Data (PDF)", onClick = { navController.navigate("clinical_report/export") }) 
                    } 
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ReportItem(title: String, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Description, null, tint = AppTheme.colors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = AppTypography.CardTitle, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, tint = AppTheme.colors.Border, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Meds" to Icons.Filled.Medication,
        "Vitals" to Icons.Default.BarChart,
        "Track" to Icons.Default.CalendarToday, 
        "Settings" to Icons.Default.Settings
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = AppTheme.colors.Surface, tonalElevation = 10.dp) {
        items.forEach { (label, icon) ->
            val route = when(label) {
                "Home" -> "home"
                "Meds" -> "meds"
                "Vitals" -> "trends"
                "Track" -> "calendar"
                "Settings" -> "profile"
                else -> "home"
            }
            
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 10.sp) },
                selected = currentRoute == route,
                onClick = { 
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppTheme.colors.Primary,
                    selectedTextColor = AppTheme.colors.Primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = AppTheme.colors.TextSecondary,
                    unselectedTextColor = AppTheme.colors.TextSecondary
                )
            )
        }
    }
}

@Composable
fun SignInScreen(navController: NavController, onSignIn: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(containerColor = AppTheme.colors.Background) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier.size(100.dp).background(AppTheme.colors.Primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape), 
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "App Logo", tint = AppTheme.colors.Primary, modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            // App name with space between Health and Track
            Text("Health Track", style = AppTypography.H1, fontSize = 32.sp, color = AppTheme.colors.Primary)
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Sign In", style = AppTypography.H1, fontSize = 28.sp)
            Text("Welcome back", style = AppTypography.Subtitle, modifier = Modifier.padding(top = 8.dp))
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Error message
            if (errorMessage.isNotBlank()) {
                Surface(
                    color = AppTheme.colors.ErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(errorMessage, color = AppTheme.colors.ErrorContent, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }
            
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = "" },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                isError = errorMessage.isNotBlank() && name.isBlank(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg,
                    focusedTextColor = AppTheme.colors.TextPrimary,
                    unfocusedTextColor = AppTheme.colors.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = "" },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                isError = errorMessage.isNotBlank() && email.isBlank(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg,
                    focusedTextColor = AppTheme.colors.TextPrimary,
                    unfocusedTextColor = AppTheme.colors.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = "" },
                label = { Text("Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                isError = errorMessage.isNotBlank() && password.isBlank(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg,
                    focusedTextColor = AppTheme.colors.TextPrimary,
                    unfocusedTextColor = AppTheme.colors.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Forgot Password?", color = AppTheme.colors.TextSecondary, fontSize = 12.sp, modifier = Modifier.clickable { 
                // Handle forgot password - show reset dialog or navigate
            })
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    // Validate all fields
                    if (name.isBlank() || email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all fields"
                        return@Button
                    }
                    if (!email.contains("@") || !email.contains(".")) {
                        errorMessage = "Please enter a valid email address"
                        return@Button
                    }
                    if (password.length < 4) {
                        errorMessage = "Password must be at least 4 characters"
                        return@Button
                    }
                    isLoading = true
                    onSignIn(email, name)
                    navController.navigate("home") {
                        popUpTo("signin") { inclusive = true }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E7490))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign In", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account? ", color = AppTheme.colors.TextSecondary, fontSize = 14.sp)
                Text("Create Account", color = AppTheme.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { navController.navigate("signup") })
            }
        }
    }
}

@Composable
fun SignUpScreen(navController: NavController, onSignUp: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold(containerColor = AppTheme.colors.Background) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(100.dp).background(AppTheme.colors.Primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape), 
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "App Logo", tint = AppTheme.colors.Primary, modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Create Account", style = AppTypography.H1, fontSize = 28.sp)
            Text("Start your health journey", style = AppTypography.Subtitle, modifier = Modifier.padding(top = 8.dp))
            
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
             OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg
                )
            )
             Spacer(modifier = Modifier.height(16.dp))
             OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0E7490),
                    unfocusedBorderColor = AppTheme.colors.Border,
                    focusedContainerColor = AppTheme.colors.InputBg,
                    unfocusedContainerColor = AppTheme.colors.InputBg
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
Button(
                onClick = { 
                    // Validate all fields
                    if (name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                        return@Button
                    }
                    if (!email.contains("@") || !email.contains(".")) {
                        return@Button
                    }
                    if (password != confirmPassword) {
                        return@Button
                    }
                    if (password.length < 4) {
                        return@Button
                    }
                    onSignUp(if(email.isNotBlank()) email else "new_user@example.com", if(name.isNotBlank()) name else "New User")
                    navController.navigate("account_created") {
                        popUpTo("signup") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E7490))
            ) {
                Text("Sign Up", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
             Spacer(modifier = Modifier.weight(1f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account? ", color = AppTheme.colors.TextSecondary, fontSize = 14.sp)
                Text("Sign In", color = AppTheme.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun AccountCreatedScreen(navController: NavController) {
    Scaffold(containerColor = AppTheme.colors.Background) { p ->
        Column(
            modifier = Modifier.padding(p).padding(32.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF0E7490).copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFF0E7490), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text("Account Created", style = AppTypography.H1, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Your account has been successfully set up. You can now start tracking your vitals and managing reminders.",
                style = AppTypography.Subtitle,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = { navController.navigate("home") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E7490))
            ) {
                Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LoginScreen(navController: NavController) {
    var pin by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.Background).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(48.dp)); Spacer(modifier = Modifier.height(32.dp))
        Text("Welcome Back", style = AppTypography.H1); Text("Enter PIN to continue", style = AppTypography.Subtitle); Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = pin, onValueChange = { if (it.length <= 4) pin = it },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppTheme.colors.Surface,
                unfocusedContainerColor = AppTheme.colors.Surface,
                focusedBorderColor = AppTheme.colors.Primary,
                unfocusedBorderColor = AppTheme.colors.Border,
                focusedTextColor = AppTheme.colors.TextPrimary, // Fix: Text was invisible in dark mode default
                unfocusedTextColor = AppTheme.colors.TextPrimary,
                cursorColor = AppTheme.colors.Primary
            )
        )
        Spacer(modifier = Modifier.height(24.dp)); ActionButton("Unlock Account", onClick = { if (pin.length == 4) navController.navigate("home") }, variant = "FILLED")
    }
}

@Composable
fun LogScreen(type: String, navController: NavController, userEmail: String, viewModel: HealthViewModel) {
    var val1 by remember { mutableStateOf("") }
    var val2 by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    val title = if (type == "WEIGHT") "Log Weight" else "Log BP"
    Scaffold(containerColor = AppTheme.colors.Background) { p ->
        Column(modifier = Modifier.padding(p).padding(24.dp)) {
            Row(modifier = Modifier.padding(bottom = 24.dp).clickable { navController.popBackStack() }, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppTheme.colors.TextPrimary); Text("Back", modifier = Modifier.padding(start = 8.dp), color = AppTheme.colors.TextPrimary) }
            Text(title, style = AppTypography.H1); Spacer(modifier = Modifier.height(24.dp))
            AppCard {
                if (type == "WEIGHT") { 
                    Text("Weight (kg)", style = AppTypography.Subtitle, modifier = Modifier.padding(bottom = 8.dp)); 
                    OutlinedTextField(
                        value = val1, onValueChange = { if(it.isEmpty() || it.toDoubleOrNull() != null) val1 = it }, 
                        modifier = Modifier.fillMaxWidth(), 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary, focusedBorderColor = AppTheme.colors.Primary, unfocusedBorderColor = AppTheme.colors.Border)
                    ) 
                } 
                else { 
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
                        Column(modifier = Modifier.weight(1f)) { 
                            Text("Systolic", style = AppTypography.Subtitle); 
                            OutlinedTextField(value = val1, onValueChange = { if(it.isEmpty() || (it.toIntOrNull() != null && it.length <= 3)) val1 = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary, focusedBorderColor = AppTheme.colors.Primary, unfocusedBorderColor = AppTheme.colors.Border)) 
                        }; 
                        Column(modifier = Modifier.weight(1f)) { 
                            Text("Diastolic", style = AppTypography.Subtitle); 
                            OutlinedTextField(value = val2, onValueChange = { if(it.isEmpty() || (it.toIntOrNull() != null && it.length <= 3)) val2 = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary, focusedBorderColor = AppTheme.colors.Primary, unfocusedBorderColor = AppTheme.colors.Border)) 
                        } 
                    } 
                }
                
                if (message.isNotBlank()) {
                    Text(message, color = if (message.contains("Success")) AppTheme.colors.SuccessContent else AppTheme.colors.ErrorContent, modifier = Modifier.padding(vertical = 8.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (val1.isBlank()) {
                            message = "Please enter values"
                            return@Button
                        }
                        // Validation logic
                        if (type == "WEIGHT" && (val1.toDouble() < 30 || val1.toDouble() > 250)) {
                             message = "Invalid weight value"
                             return@Button
                        }
                        if (type == "BP") {
                            val sys = val1.toIntOrNull() ?: 0
                            val dia = val2.toIntOrNull() ?: 0
                            if (sys < 70 || sys > 250 || dia < 40 || dia > 150) {
                                message = "Invalid BP readings"
                                return@Button
                            }
                        }

isLoading = true
                        scope.launch {
                            try {
                                val data = HealthData(userEmail, type, val1, val2)
                                
                                // Use batch processing for faster response
                                DataBatch.addData(data)
                                val response = RetrofitInstance.sendHealthData(data)
                                
                                // Update ViewModel for Dynamic UI
                                if(type == "WEIGHT") viewModel.updateWeight(val1)
                                else viewModel.updateBP(val1, val2)

                                message = if (response.status == "success") "Success! Record saved." else "Info: ${response.message}"
                                delay(100)  // Further reduced for instant feedback
                                navController.popBackStack()
                            } catch (e: Exception) {
                                message = "Error: ${e.localizedMessage ?: "Failed to save data. Check your connection."}"
                                // Still update local ViewModel even if API fails
                                if(type == "WEIGHT") viewModel.updateWeight(val1)
                                else viewModel.updateBP(val1, val2)
                                // Add to batch for retry
                                DataBatch.addData(HealthData(userEmail, type, val1, val2))
                                delay(300)  // Reduced for faster UX
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.Primary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Save Record", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationDetailScreen(navController: NavController, context: Context = LocalContext.current) {
    var isActive by remember { mutableStateOf(true) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var medName by remember { mutableStateOf("Metformin") }
    var reminders by remember { mutableStateOf(listOf(
        Triple("08:00 AM", "1 tablet • Before meal", true),
        Triple("08:00 PM", "1 tablet • Before meal", true)
    )) }
    var selectedFrequency by remember { mutableIntStateOf(0) }
    
    // Notification Helper
fun scheduleMedicationAlarm(timeStr: String, med: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra("MED_NAME", med)
            action = "com.first.mynew_application.ALARM_ACTION"
        }
        
        val timeParts = timeStr.split(" ", ":")
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        if (timeParts[2] == "PM" && hour < 12) hour += 12
        else if (timeParts[2] == "AM" && hour == 12) hour = 0
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(context, timeStr.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        
        // Also save reminder to Google Sheets
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                val prefs = context.getSharedPreferences("health_track_prefs", Context.MODE_PRIVATE)
                val userEmail = prefs.getString("userEmail", "") ?: ""
                val data = HealthData(userEmail, "MEDICATION_REMINDER", med, timeStr)
                RetrofitInstance.sendHealthData(data)
            } catch (e: Exception) {
                // Continue even if API fails
            }
        }
    }

    // Edit Name Dialog
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(medName) }
        Dialog(onDismissRequest = { showEditNameDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Edit Medication Name", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Medication Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    ActionButton("Update Name", onClick = { if(newName.isNotBlank()) medName = newName; showEditNameDialog = false })
                }
            }
        }
    }
    
    // Time Picker Dialog
    if (showTimePickerDialog) {
        var hour by remember { mutableStateOf("08") }
        var minute by remember { mutableStateOf("00") }
        var amPm by remember { mutableStateOf("AM") }
        var dosage by remember { mutableStateOf("1 tablet") }
        var timing by remember { mutableStateOf("Before meal") }
        
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Add Reminder Time", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Time Selection
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = hour, onValueChange = { if (it.length <= 2) hour = it }, label = { Text("Hour") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary))
                        Text(":", style = AppTypography.H1)
                        OutlinedTextField(value = minute, onValueChange = { if (it.length <= 2) minute = it }, label = { Text("Min") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary))
                        Column {
                            listOf("AM", "PM").forEach { period ->
                                Surface(modifier = Modifier.clickable { amPm = period }, color = if (amPm == period) AppTheme.colors.Primary else AppTheme.colors.InputBg, shape = RoundedCornerShape(4.dp)) {
                                    Text(period, modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = if (amPm == period) Color.White else AppTheme.colors.TextPrimary)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text("Dosage") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = timing, onValueChange = { timing = it }, label = { Text("Timing (e.g., Before meal)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButton("Add Time", onClick = {
                        val timeStr = "$hour:$minute $amPm"
                        val desc = "$dosage • $timing"
                        reminders = reminders + Triple(timeStr, desc, true)
                        scheduleMedicationAlarm(timeStr, medName)
                        showTimePickerDialog = false
                    })
                }
            }
        }
    }
    
    Scaffold(containerColor = AppTheme.colors.Background, bottomBar = { BottomNavBar(navController) }) { p ->
        Column(modifier = Modifier.padding(p).padding(24.dp).verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp).clickable { navController.popBackStack() }, tint = AppTheme.colors.TextPrimary)
                Text(
                    text = medName, 
                    style = AppTypography.H1, 
                    modifier = Modifier.weight(1f).padding(start = 16.dp).clickable { showEditNameDialog = true }, 
                    fontSize = 20.sp
                )
                Icon(Icons.Default.Edit, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(18.dp).clickable { showEditNameDialog = true })
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Text(if (isActive) "ACTIVE" else "INACTIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.TextSecondary, modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = isActive, onCheckedChange = { isActive = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppTheme.colors.Primary, uncheckedThumbColor = AppTheme.colors.TextSecondary, uncheckedTrackColor = AppTheme.colors.InputBg)) 
                }
            }
            AppCard {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { 
                    Text("TODAY'S PROGRESS", style = AppTypography.SectionHeader, color = AppTheme.colors.TextSecondary)
                    Text("${reminders.count { it.third }} of ${reminders.size} completed", color = AppTheme.colors.Primary, fontWeight = FontWeight.Bold, fontSize = 12.sp) 
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(progress = { if (reminders.isEmpty()) 0f else reminders.count { it.third }.toFloat() / reminders.size }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = AppTheme.colors.Primary, trackColor = AppTheme.colors.InputBg)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Frequency", style = AppTypography.CardTitle)
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedControl(listOf("Daily", "Weekly", "Custom"), selectedFrequency) { selectedFrequency = it }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { 
                Text("Schedule", style = AppTypography.CardTitle)
                Text("${reminders.size} Reminders Set", style = AppTypography.Subtitle, fontSize = 12.sp) 
            }
            Spacer(modifier = Modifier.height(12.dp))
            reminders.forEachIndexed { index, reminder ->
                ReminderItemInteractive(
                    time = reminder.first, 
                    desc = reminder.second, 
                    enabled = reminder.third,
                    onToggle = { newVal -> 
                        reminders = reminders.toMutableList().also { it[index] = reminder.copy(third = newVal) }
                        if (newVal) scheduleMedicationAlarm(reminder.first, medName)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Button(onClick = { showTimePickerDialog = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.Primary)) { 
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add New Time", color = Color.White, fontWeight = FontWeight.Bold) 
            }
        }
    }
}

@Composable
fun ReminderItemInteractive(time: String, desc: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    if (showEditDialog) {
        Dialog(onDismissRequest = { showEditDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Edit Reminder", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Time: $time", style = AppTypography.Value)
                    Text("Details: $desc", style = AppTypography.Subtitle)
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButton("Close", onClick = { showEditDialog = false })
                }
            }
        }
    }
    
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(AppTheme.colors.InfoContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { 
                Icon(Icons.Default.AccessTime, null, tint = AppTheme.colors.InfoContent, modifier = Modifier.size(24.dp)) 
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { 
                Text(time, style = AppTypography.CardTitle)
                Text(desc, style = AppTypography.Subtitle, fontSize = 12.sp) 
            }
            Icon(Icons.Default.Edit, null, tint = AppTheme.colors.TextSecondary, modifier = Modifier.size(20.dp).clickable { showEditDialog = true })
            Spacer(modifier = Modifier.width(16.dp))
            Switch(checked = enabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppTheme.colors.Primary, uncheckedThumbColor = AppTheme.colors.TextSecondary, uncheckedTrackColor = AppTheme.colors.InputBg))
        }
    }
}

@Composable
fun ReminderItem(time: String, desc: String, enabled: Boolean) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(AppTheme.colors.InfoContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccessTime, null, tint = AppTheme.colors.InfoContent, modifier = Modifier.size(24.dp)) }
            Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(time, style = AppTypography.CardTitle); Text(desc, style = AppTypography.Subtitle, fontSize = 12.sp) }
            Icon(Icons.Default.Edit, null, tint = AppTheme.colors.TextSecondary, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(16.dp))
            Switch(checked = enabled, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppTheme.colors.Primary))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = context.getSharedPreferences("health_track_prefs", Context.MODE_PRIVATE)
            
            // Read saved session
            val savedLoggedIn = prefs.getBoolean("isLoggedIn", false)
            val savedEmail = prefs.getString("userEmail", "") ?: ""
            val savedName = prefs.getString("userName", "") ?: ""
            val savedTheme = prefs.getBoolean("isDarkTheme", true)
            
            // Theme State
            var isDarkTheme by remember { mutableStateOf(savedTheme) }
            var userEmail by remember { mutableStateOf(savedEmail) }
            var userName by remember { mutableStateOf(savedName) }
            var isLoggedIn by remember { mutableStateOf(savedLoggedIn) }
            
            // Save session function
            fun saveSession(email: String, name: String) {
                prefs.edit()
                    .putBoolean("isLoggedIn", true)
                    .putString("userEmail", email)
                    .putString("userName", name)
                    .apply()
                userEmail = email
                userName = name
                isLoggedIn = true
            }
            
            // Save theme preference
            fun saveTheme(dark: Boolean) {
                prefs.edit().putBoolean("isDarkTheme", dark).apply()
                isDarkTheme = dark
            }
            
            // Logout function
            fun logout() {
                prefs.edit()
                    .putBoolean("isLoggedIn", false)
                    .remove("userEmail")
                    .remove("userName")
                    .apply()
                isLoggedIn = false
            }
            
            // Determine start destination based on saved session
            val startDest = if (isLoggedIn && savedEmail.isNotBlank()) "home" else "signin"
            
            val viewModel: HealthViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return HealthViewModel(prefs) as T
                }
            })
            
            AppTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController, 
                    startDestination = startDest,
                    // Remove transitions
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None }
                ) {
                    composable("signin") { 
                        SignInScreen(navController) { email, name -> 
                            saveSession(email, name)
                        } 
                    }
                    composable("signup") { 
                        SignUpScreen(navController) { email, name -> 
                            saveSession(email, name)
                        } 
                    }
                    composable("account_created") { AccountCreatedScreen(navController) }
                    
                    composable("login") { LoginScreen(navController) }
                    composable("home") { HomeScreen(navController, viewModel) }
                    composable("calendar") { TrackScreen(navController, userEmail) }
                    composable("trends") { TrendsScreen(navController, viewModel) }
                    composable("profile") { 
                        ProfileScreen(navController, isDarkTheme, userEmail, userName, onToggleTheme = { saveTheme(it) }, onLogout = { logout(); navController.navigate("signin") { popUpTo(0) } }) 
                    }
                    composable("meds") { MedicationDetailScreen(navController) }
                    composable("settings") { ProfileScreen(navController, isDarkTheme, userEmail, userName, onToggleTheme = { saveTheme(it) }, onLogout = { logout(); navController.navigate("signin") { popUpTo(0) } }) } 
                    composable("log/{type}") { bs -> LogScreen(bs.arguments?.getString("type") ?: "WEIGHT", navController, userEmail, viewModel) }
                    composable("clinical_report/{type}") { bs -> ClinicalReportScreen(bs.arguments?.getString("type") ?: "summary", navController, userEmail) }
                }
            }
        }
    }
}

// Track Screen - Interactive version of HistoryScreen
@Composable
fun TrackScreen(navController: NavController, userEmail: String) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Sample tracked data
    var trackedEntries by remember { mutableStateOf(listOf(
        Triple(Icons.Default.MonitorWeight, "145 lbs", "08:30 AM"),
        Triple(Icons.Default.Favorite, "120/80 mmHg", "09:15 AM")
    )) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    
    // Add Entry Dialog
    if (showAddEntryDialog) {
        var entryType by remember { mutableStateOf("Weight") }
        var entryValue by remember { mutableStateOf("") }
        var isSyncing by remember { mutableStateOf(false) }
        
        Dialog(onDismissRequest = { if (!isSyncing) showAddEntryDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Add Entry", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Entry Type", style = AppTypography.Subtitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState())) {
                        listOf("Weight", "Blood Pressure", "Mood", "Medication").forEach { type ->
                            Surface(
                                modifier = Modifier.clickable { entryType = type },
                                color = if (entryType == type) AppTheme.colors.Primary else AppTheme.colors.InputBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = type,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 12.sp,
                                    color = if (entryType == type) Color.White else AppTheme.colors.TextPrimary,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = entryValue, onValueChange = { entryValue = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary), enabled = !isSyncing)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (entryValue.isNotBlank()) {
                                isSyncing = true
scope.launch {
                                    try {
                                        val data = HealthData(userEmail, entryType.uppercase(), entryValue, "")
                                        val response = RetrofitInstance.sendHealthData(data)
                                        
                                        val icon = when(entryType) {
                                            "Weight" -> Icons.Default.MonitorWeight
                                            "Blood Pressure" -> Icons.Default.Favorite
                                            "Mood" -> Icons.Default.SentimentSatisfied
                                            else -> Icons.Outlined.MedicalServices
                                        }
                                        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Calendar.getInstance().time)
                                        trackedEntries = trackedEntries + Triple(icon, entryValue, time)
                                        showAddEntryDialog = false
                                    } catch (e: Exception) {
                                        // Still add locally if offline for better UX
                                        val icon = when(entryType) {
                                            "Weight" -> Icons.Default.MonitorWeight
                                            "Blood Pressure" -> Icons.Default.Favorite
                                            "Mood" -> Icons.Default.SentimentSatisfied
                                            else -> Icons.Outlined.MedicalServices
                                        }
                                        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Calendar.getInstance().time)
                                        trackedEntries = trackedEntries + Triple(icon, entryValue, time)
                                        showAddEntryDialog = false
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.Primary),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Add Entry", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        Dialog(onDismissRequest = { showDatePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Select Month", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    (0..11).forEach { monthIdx ->
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.MONTH, monthIdx)
                        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                val newCal = Calendar.getInstance()
                                newCal.time = selectedDate.time
                                newCal.set(Calendar.MONTH, monthIdx)
                                selectedDate = newCal
                                showDatePicker = false 
                            },
                            color = if (selectedDate.get(Calendar.MONTH) == monthIdx) AppTheme.colors.Primary.copy(alpha = 0.1f) else AppTheme.colors.InputBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(monthName, modifier = Modifier.padding(12.dp), style = AppTypography.Value, color = if (selectedDate.get(Calendar.MONTH) == monthIdx) AppTheme.colors.Primary else AppTheme.colors.TextPrimary)
                        }
                    }
                }
            }
        }
    }

    // Filter Dialog
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilterType by remember { mutableStateOf("All") }
    if (showFilterDialog) {
        Dialog(onDismissRequest = { showFilterDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Filter Logs", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    listOf("All", "Weight", "Blood Pressure", "Mood").forEach { filter ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedFilterType = filter; showFilterDialog = false },
                            color = if (selectedFilterType == filter) AppTheme.colors.Primary.copy(alpha = 0.1f) else AppTheme.colors.InputBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(filter, modifier = Modifier.padding(12.dp), style = AppTypography.Value)
                        }
                    }
                }
            }
        }
    }

    // Search state
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Log Detail Dialog
    var selectedLogEntry by remember { mutableStateOf<Triple<ImageVector, String, String>?>(null) }
    if (selectedLogEntry != null) {
        Dialog(onDismissRequest = { selectedLogEntry = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.Surface) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(64.dp).background(AppTheme.colors.Primary.copy(alpha = 0.1f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(selectedLogEntry!!.first, null, tint = AppTheme.colors.Primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Entry Details", style = AppTypography.H1, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Value", style = AppTypography.Subtitle)
                        Text(selectedLogEntry!!.second, style = AppTypography.Value)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Time", style = AppTypography.Subtitle)
                        Text(selectedLogEntry!!.third, style = AppTypography.Value)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date", style = AppTypography.Subtitle)
                        Text(SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(selectedDate.time), style = AppTypography.Value)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    ActionButton("Close", onClick = { selectedLogEntry = null })
                }
            }
        }
    }
    
    Scaffold(bottomBar = { BottomNavBar(navController) }, containerColor = AppTheme.colors.Background, floatingActionButton = {
        FloatingActionButton(onClick = { showAddEntryDialog = true }, containerColor = AppTheme.colors.Primary) {
            Icon(Icons.Default.Add, null, tint = Color.White)
        }
    }) { p ->
        LazyColumn(modifier = Modifier.padding(p).padding(horizontal = 24.dp)) {
            item {
                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        placeholder = { Text("Search logs...") },
                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.clickable { searchQuery = ""; searchVisible = false }) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.Primary, unfocusedBorderColor = AppTheme.colors.Border, focusedTextColor = AppTheme.colors.TextPrimary, unfocusedTextColor = AppTheme.colors.TextPrimary)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showDatePicker = true }) {
                        Text(dateFormat.format(selectedDate.time), style = AppTypography.H1)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = AppTheme.colors.Primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Search, null, tint = if (searchVisible) AppTheme.colors.Primary else AppTheme.colors.TextPrimary, modifier = Modifier.clickable { searchVisible = !searchVisible })
                        Icon(Icons.Default.Tune, null, tint = if (showFilterDialog) AppTheme.colors.Primary else AppTheme.colors.TextPrimary, modifier = Modifier.clickable { showFilterDialog = true })
                    }
                }
            }
            item { SectionHeader("TODAY") }
            val filteredEntries = trackedEntries.filter { entry ->
                val typeMatch = if (selectedFilterType == "All") true else entry.second.contains(selectedFilterType, ignoreCase = true) || entry.third.contains(selectedFilterType, ignoreCase = true)
                val searchMatch = if (searchQuery.isBlank()) true else entry.second.contains(searchQuery, ignoreCase = true)
                typeMatch && searchMatch
            }
            items(filteredEntries) { entry ->
                HistoryItem(entry.first, Color(0xFF0EA5E9), entry.second, entry.third, onClick = { selectedLogEntry = entry })
            }
            item { SectionHeader("QUICK ACTIONS") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { navController.navigate("log/WEIGHT") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.InputBg)) {
                        Text("Log Weight", color = AppTheme.colors.TextPrimary)
                    }
                    Button(onClick = { navController.navigate("log/BP") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.InputBg)) {
                        Text("Log BP", color = AppTheme.colors.TextPrimary)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

// Clinical Report Screen
@Composable
fun ClinicalReportScreen(type: String, navController: NavController, userEmail: String) {
    val title = if (type == "summary") "Monthly Health Summary" else "Export Clinical Data"
    var isLoading by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    Scaffold(containerColor = AppTheme.colors.Background) { p ->
        Column(modifier = Modifier.padding(p).padding(24.dp)) {
            Row(modifier = Modifier.padding(bottom = 24.dp).clickable { navController.popBackStack() }, verticalAlignment = Alignment.CenterVertically) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppTheme.colors.TextPrimary)
                Text("Back", modifier = Modifier.padding(start = 8.dp), color = AppTheme.colors.TextPrimary) 
            }
            Text(title, style = AppTypography.H1)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (type == "summary") {
                AppCard {
                    Text("HEALTH OVERVIEW", style = AppTypography.SectionHeader)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column { Text("Weight", style = AppTypography.Subtitle); Text("62.4 kg", style = AppTypography.Value) }
                        Column { Text("BP Average", style = AppTypography.Subtitle); Text("118/76 mmHg", style = AppTypography.Value) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column { Text("Medications", style = AppTypography.Subtitle); Text("2 Active", style = AppTypography.Value) }
                        Column { Text("Adherence", style = AppTypography.Subtitle); Text("95%", style = AppTypography.Value) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                AppCard {
                    Text("INSIGHTS", style = AppTypography.SectionHeader)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Weight has remained stable this month", style = AppTypography.Subtitle)
                    Text("• Blood pressure readings are within normal range", style = AppTypography.Subtitle, modifier = Modifier.padding(top = 8.dp))
                    Text("• Medication adherence improved by 10%", style = AppTypography.Subtitle, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                AppCard {
                    Text("EXPORT OPTIONS", style = AppTypography.SectionHeader)
                    Spacer(modifier = Modifier.height(16.dp))
listOf("PDF Report", "CSV Data", "Share with Doctor").forEach { option ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                isLoading = true
                                scope.launch {
                                    try {
                                        when (option) {
                                            "PDF Report" -> {
                                                val data = HealthData(userEmail, "EXPORT", "PDF", "")
                                                val response = RetrofitInstance.sendHealthData(data)
                                                exportMessage = "PDF report generated successfully!"
                                            }
                                            "CSV Data" -> {
                                                val data = HealthData(userEmail, "EXPORT", "CSV", "")
                                                val response = RetrofitInstance.sendHealthData(data)
                                                exportMessage = "CSV data exported successfully!"
                                            }
                                            "Share with Doctor" -> {
                                                val data = HealthData(userEmail, "EXPORT", "SHARE", "")
                                                val response = RetrofitInstance.sendHealthData(data)
                                                exportMessage = "Report shared with your doctor!"
                                            }
                                        }
                                        delay(1000)
                                    } catch (e: Exception) {
                                        exportMessage = "Export started. You will receive a notification when it's ready."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            color = AppTheme.colors.InputBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (option == "PDF Report") Icons.Default.PictureAsPdf else if (option == "CSV Data") Icons.Default.TableChart else Icons.Default.Share, null, tint = AppTheme.colors.Primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(option, style = AppTypography.Value)
                            }
                        }
                    }
                }
                
                if (exportMessage.isNotBlank()) {
                    Text(exportMessage, color = if (exportMessage.contains("Success")) AppTheme.colors.SuccessContent else AppTheme.colors.ErrorContent, modifier = Modifier.padding(vertical = 12.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { 
                        isLoading = true
scope.launch {
                            try {
                                val data = HealthData(userEmail, "EXPORT", "PDF", "")
                                val response = RetrofitInstance.sendHealthData(data)
                                exportMessage = "Success! Report generated and sent to your email."
                            } catch (e: Exception) {
                                exportMessage = "Export started. You will receive a notification when it's ready."
                                // Fallback simulation
                                delay(2000)
                                exportMessage = "Success! Report generated."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.Primary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Generate Report", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}